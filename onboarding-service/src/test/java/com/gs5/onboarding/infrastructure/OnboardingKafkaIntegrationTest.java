package com.gs5.onboarding.infrastructure;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3, topics = {"screening.client-events.v1", "restriction.client-events.v1"})
class OnboardingKafkaIntegrationTest {

    private static final String ONBOARDING_TOPIC = "onboarding.customer-events.v1";
    private static final String SCREENING_TOPIC = "screening.client-events.v1";
    private static final String RESTRICTION_TOPIC = "restriction.client-events.v1";
    private static final String DEAD_LETTER_TOPIC = "restriction.client-events.v1.onboarding-service.dlt";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired JdbcClient jdbc;

    private Consumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    @BeforeEach
    void subscribe() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("test-" + UUID.randomUUID(), "false", broker);
        props.put("auto.offset.reset", "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        consumer.subscribe(List.of(ONBOARDING_TOPIC, DEAD_LETTER_TOPIC));
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    @Test
    void publishesApplicantVerifiedKeyedByCustomerIdAndWaitsForScreening() throws Exception {
        String customerId = startOnboarding("Anna", "Kowalska");

        ConsumerRecord<String, String> record = awaitRecord(ONBOARDING_TOPIC, customerId);
        JsonNode event = objectMapper.readTree(record.value());

        assertThat(event.get("eventType").asText()).isEqualTo("ApplicantVerified");
        assertThat(event.get("aggregateVersion").asLong()).isEqualTo(2);
        assertThat(event.at("/payload/lastName").asText()).isEqualTo("Kowalska");
        assertThat(new String(record.headers().lastHeader("event-type").value())).isEqualTo("ApplicantVerified");
        mvc.perform(get("/api/onboardings/{id}", customerId))
                .andExpect(jsonPath("$.status").value("PENDING_SCREENING"));
    }

    @Test
    void activatesCustomerWhenScreeningClearsThem() throws Exception {
        String customerId = startOnboarding("Ewa", "Zielinska");

        kafka.send(SCREENING_TOPIC, customerId, envelope(UUID.randomUUID(), "ClientCleared", customerId,
                "{\"customerId\":\"%s\"}".formatted(customerId))).get();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                mvc.perform(get("/api/onboardings/{id}", customerId))
                        .andExpect(jsonPath("$.status").value("ONBOARDED")));
        JsonNode onboarded = objectMapper.readTree(
                awaitRecord(ONBOARDING_TOPIC, customerId, "CustomerOnboarded").value());
        assertThat(onboarded.get("aggregateVersion").asLong()).isEqualTo(3);
    }

    @Test
    void banWinsWhenClearanceArrivesAfterIt() throws Exception {
        String customerId = startOnboarding("Piotr", "Lis");
        kafka.send(RESTRICTION_TOPIC, customerId, envelope(UUID.randomUUID(), "ClientBanned", customerId,
                "{\"customerId\":\"%s\",\"reason\":\"Manual ban\"}".formatted(customerId))).get();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                mvc.perform(get("/api/onboardings/{id}", customerId))
                        .andExpect(jsonPath("$.status").value("BANNED")));

        kafka.send(SCREENING_TOPIC, customerId, envelope(UUID.randomUUID(), "ClientCleared", customerId,
                "{\"customerId\":\"%s\"}".formatted(customerId))).get();

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(eventsOfType(customerId, "CustomerOnboarded")).isZero());
        mvc.perform(get("/api/onboardings/{id}", customerId))
                .andExpect(jsonPath("$.status").value("BANNED"));
    }

    @Test
    void bansOnboardingExactlyOnceWhenClientBannedIsDeliveredTwice() throws Exception {
        String customerId = startOnboarding("Jan", "Nowak");
        String clientBanned = envelope(UUID.randomUUID(), "ClientBanned", customerId,
                """
                {"customerId":"%s","reason":"Surname on blacklist","source":"SANCTIONS_SCREENING"}
                """.formatted(customerId));

        kafka.send(RESTRICTION_TOPIC, customerId, clientBanned).get();
        kafka.send(RESTRICTION_TOPIC, customerId, clientBanned).get();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                mvc.perform(get("/api/onboardings/{id}", customerId))
                        .andExpect(jsonPath("$.status").value("BANNED"))
                        .andExpect(jsonPath("$.statusReason").value("Surname on blacklist")));
        awaitRecord(ONBOARDING_TOPIC, customerId, "OnboardingBanned");
        assertThat(eventsOfType(customerId, "OnboardingBanned")).isEqualTo(1);
    }

    @Test
    void routesMalformedMessageToDeadLetterTopic() {
        String key = UUID.randomUUID().toString();

        kafka.send(RESTRICTION_TOPIC, key, "definitely-not-json");

        assertThat(awaitRecord(DEAD_LETTER_TOPIC, key).value()).isEqualTo("definitely-not-json");
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

    private ConsumerRecord<String, String> awaitRecord(String topic, String key) {
        return awaitRecord(topic, key, null);
    }

    private ConsumerRecord<String, String> awaitRecord(String topic, String key, String eventType) {
        return await().atMost(Duration.ofSeconds(15)).until(() -> {
            KafkaTestUtils.getRecords(consumer, Duration.ofMillis(200)).forEach(received::add);
            return received.stream()
                    .filter(r -> r.topic().equals(topic) && key.equals(r.key()))
                    .filter(r -> eventType == null || r.value().contains("\"eventType\":\"" + eventType + "\""))
                    .findFirst();
        }, Optional::isPresent).orElseThrow();
    }

    private long eventsOfType(String customerId, String type) {
        return jdbc.sql("SELECT COUNT(*) FROM event_store WHERE stream_id = :stream AND event_type = :type")
                .param("stream", "customer-onboarding-" + customerId)
                .param("type", type)
                .query(Long.class).single();
    }

    private static String envelope(UUID eventId, String type, String aggregateId, String payload) {
        return """
                {"eventId":"%s","eventType":"%s","aggregateId":"%s","aggregateVersion":1,
                 "occurredAt":"2026-11-17T10:00:00Z","payload":%s}
                """.formatted(eventId, type, aggregateId, payload);
    }
}
