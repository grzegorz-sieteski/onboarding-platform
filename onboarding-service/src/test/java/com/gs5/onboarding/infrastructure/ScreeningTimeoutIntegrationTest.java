package com.gs5.onboarding.infrastructure;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:onboarding-timeout;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.screening.timeout=1s",
        "app.screening.check-interval-ms=100"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3, topics = {"screening.client-events.v1", "restriction.client-events.v1"})
class ScreeningTimeoutIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired JdbcClient jdbc;

    @Test
    void escalatesUnansweredScreeningAndOfficerApprovesIt() throws Exception {
        String customerId = startOnboarding("Marek", "Wolny");

        awaitStatus(customerId, "MANUAL_REVIEW");
        mvc.perform(get("/api/manual-reviews"))
                .andExpect(jsonPath("$[*].customerId", hasItem(customerId)));

        mvc.perform(post("/api/manual-reviews/{id}/approve", customerId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewer\":\"officer.anna\",\"comment\":\"Verified by phone\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONBOARDED"));

        mvc.perform(get("/api/manual-reviews"))
                .andExpect(jsonPath("$[*].customerId", not(hasItem(customerId))));
        assertThat(eventTypes(customerId)).containsExactly(
                "OnboardingStarted", "ApplicantVerified", "ManualReviewRequested",
                "ManualReviewApproved", "CustomerOnboarded");
        assertThat(outboxEventTypes(customerId)).containsExactly("ApplicantVerified", "CustomerOnboarded");
    }

    @Test
    void officerRejectsEscalatedOnboarding() throws Exception {
        String customerId = startOnboarding("Iza", "Krol");
        awaitStatus(customerId, "MANUAL_REVIEW");

        mvc.perform(post("/api/manual-reviews/{id}/reject", customerId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewer\":\"officer.anna\",\"reason\":\"Identity not confirmed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mvc.perform(post("/api/manual-reviews/{id}/approve", customerId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewer\":\"officer.anna\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void lateClearanceResolvesManualReviewAutomatically() throws Exception {
        String customerId = startOnboarding("Olga", "Mazur");
        awaitStatus(customerId, "MANUAL_REVIEW");

        kafka.send("screening.client-events.v1", customerId, """
                {"eventId":"%s","eventType":"ClientCleared","aggregateId":"%s","aggregateVersion":2,
                 "occurredAt":"2026-11-17T10:00:00Z","payload":{"customerId":"%s"}}
                """.formatted(UUID.randomUUID(), customerId, customerId)).get();

        awaitStatus(customerId, "ONBOARDED");
        mvc.perform(get("/api/manual-reviews"))
                .andExpect(jsonPath("$[*].customerId", not(hasItem(customerId))));
    }

    private String startOnboarding(String firstName, String lastName) throws Exception {
        String response = mvc.perform(post("/api/onboardings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"%s","lastName":"%s","email":"%s@example.com","dateOfBirth":"1990-01-01"}
                                """.formatted(firstName, lastName, firstName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_SCREENING"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("customerId").asText();
    }

    private void awaitStatus(String customerId, String expected) {
        await().atMost(WAIT).untilAsserted(() ->
                mvc.perform(get("/api/onboardings/{id}", customerId))
                        .andExpect(jsonPath("$.status").value(expected)));
    }

    private List<String> eventTypes(String customerId) {
        return jdbc.sql("SELECT event_type FROM event_store WHERE stream_id = :stream ORDER BY stream_version")
                .param("stream", "customer-onboarding-" + customerId)
                .query(String.class).list();
    }

    private List<String> outboxEventTypes(String customerId) {
        return jdbc.sql("SELECT event_type FROM outbox WHERE message_key = :key ORDER BY id")
                .param("key", customerId)
                .query(String.class).list();
    }
}
