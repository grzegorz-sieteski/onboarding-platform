package com.gs5.restriction.infrastructure;

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
@EmbeddedKafka(partitions = 3, topics = "screening.client-events.v1")
class RestrictionKafkaIntegrationTest {

    private static final String SCREENING_TOPIC = "screening.client-events.v1";
    private static final String RESTRICTION_TOPIC = "restriction.client-events.v1";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired EmbeddedKafkaBroker broker;

    private Consumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    @BeforeEach
    void subscribe() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("test-" + UUID.randomUUID(), "false", broker);
        props.put("auto.offset.reset", "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        consumer.subscribe(List.of(RESTRICTION_TOPIC));
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    @Test
    void bansClientOnceEvenIfRestrictedTwice() throws Exception {
        String customerId = UUID.randomUUID().toString();

        kafka.send(SCREENING_TOPIC, customerId, clientRestricted(customerId)).get();
        kafka.send(SCREENING_TOPIC, customerId, clientRestricted(customerId)).get();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                mvc.perform(get("/api/restrictions/{id}", customerId))
                        .andExpect(jsonPath("$.banned").value(true))
                        .andExpect(jsonPath("$.source").value("SANCTIONS_SCREENING")));
        List<ConsumerRecord<String, String>> published = recordsFor(customerId, Duration.ofSeconds(2));
        assertThat(published).hasSize(1);
        JsonNode event = objectMapper.readTree(published.getFirst().value());
        assertThat(event.get("eventType").asText()).isEqualTo("ClientBanned");
        assertThat(event.at("/payload/reason").asText()).isEqualTo("Sanctions list EU-2026/17");
    }

    @Test
    void manualBanIsPublishedToo() throws Exception {
        String customerId = UUID.randomUUID().toString();

        mvc.perform(post("/api/restrictions/{id}/ban", customerId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Fraud investigation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banned").value(true));

        assertThat(recordsFor(customerId, Duration.ofSeconds(2))).singleElement()
                .satisfies(record -> assertThat(record.value()).contains("\"source\":\"MANUAL\""));
    }

    private List<ConsumerRecord<String, String>> recordsFor(String key, Duration quietPeriod) {
        long deadline = System.currentTimeMillis() + quietPeriod.toMillis();
        while (System.currentTimeMillis() < deadline) {
            KafkaTestUtils.getRecords(consumer, Duration.ofMillis(200)).forEach(received::add);
        }
        return received.stream().filter(r -> key.equals(r.key())).toList();
    }

    private static String clientRestricted(String customerId) {
        return """
                {"eventId":"%s","eventType":"ClientRestricted","aggregateId":"%s","aggregateVersion":2,
                 "occurredAt":"2026-11-17T10:00:00Z",
                 "payload":{"customerId":"%s","matchedSurname":"Michałowski","reason":"Sanctions list EU-2026/17"}}
                """.formatted(UUID.randomUUID(), customerId, customerId);
    }
}
