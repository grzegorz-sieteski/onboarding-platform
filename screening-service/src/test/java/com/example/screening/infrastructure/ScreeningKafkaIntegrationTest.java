package com.example.screening.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
@EmbeddedKafka(partitions = 3, topics = "onboarding.customer-events.v1")
class ScreeningKafkaIntegrationTest {

    private static final String ONBOARDING_TOPIC = "onboarding.customer-events.v1";
    private static final String SCREENING_TOPIC = "screening.client-events.v1";

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
        consumer.subscribe(List.of(SCREENING_TOPIC));
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    @Test
    void publishesClientRestrictedWhenSurnameIsBlacklisted() throws Exception {
        mvc.perform(post("/api/blacklist").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastName":"Michałowski","reason":"Sanctions list EU-2026/17"}
                                """))
                .andExpect(status().isCreated());
        String customerId = UUID.randomUUID().toString();

        kafka.send(ONBOARDING_TOPIC, customerId, applicantVerified(customerId, "MICHALOWSKI")).get();

        JsonNode event = objectMapper.readTree(awaitRecord(customerId).value());
        assertThat(event.get("eventType").asText()).isEqualTo("ClientRestricted");
        assertThat(event.at("/payload/reason").asText()).isEqualTo("Sanctions list EU-2026/17");
        mvc.perform(get("/api/screenings/{id}", customerId))
                .andExpect(jsonPath("$.status").value("RESTRICTED"));
    }

    @Test
    void publishesClientClearedAndScreensOnlyOnceOnRedelivery() throws Exception {
        String customerId = UUID.randomUUID().toString();
        String message = applicantVerified(customerId, "Nowak");

        kafka.send(ONBOARDING_TOPIC, customerId, message).get();
        kafka.send(ONBOARDING_TOPIC, customerId, message).get();

        JsonNode event = objectMapper.readTree(awaitRecord(customerId).value());
        assertThat(event.get("eventType").asText()).isEqualTo("ClientCleared");
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5)).until(() ->
                jdbc.sql("SELECT COUNT(*) FROM event_store WHERE stream_id = :stream")
                        .param("stream", "screening-case-" + customerId)
                        .query(Long.class).single() == 2);
    }

    private ConsumerRecord<String, String> awaitRecord(String key) {
        return await().atMost(Duration.ofSeconds(15)).until(() -> {
            KafkaTestUtils.getRecords(consumer, Duration.ofMillis(200)).forEach(received::add);
            return received.stream().filter(r -> key.equals(r.key())).findFirst();
        }, Optional::isPresent).orElseThrow();
    }

    private static String applicantVerified(String customerId, String lastName) {
        return """
                {"eventId":"%s","eventType":"ApplicantVerified","aggregateId":"%s","aggregateVersion":2,
                 "occurredAt":"2026-11-17T10:00:00Z",
                 "payload":{"customerId":"%s","firstName":"Adam","lastName":"%s","dateOfBirth":"1990-01-01"}}
                """.formatted(UUID.randomUUID(), customerId, customerId, lastName);
    }
}
