package com.gs5.onboarding.infrastructure;

import com.gs5.onboarding.application.ApproveManualReviewCommand;
import com.gs5.onboarding.application.OnboardingCommandHandler;
import com.gs5.onboarding.application.OnboardingQueries;
import com.gs5.onboarding.application.StartOnboardingCommand;
import com.gs5.onboarding.domain.CustomerId;
import com.gs5.onboarding.domain.CustomerOnboarding;
import com.gs5.onboarding.domain.OnboardingError;
import com.gs5.onboarding.domain.OnboardingError.ConcurrentModification;
import com.gs5.onboarding.domain.shared.Result;
import com.gs5.onboarding.infrastructure.eventstore.JdbcEventStore;
import com.gs5.onboarding.infrastructure.messaging.OnboardingIntegrationEvents;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * What happens when a command is accepted by the domain but {@code save} does not go through.
 * Both ways of failing must leave the database exactly as it was: nothing half written.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:onboarding-save-failure;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.screening.timeout=1s",
        "app.screening.check-interval-ms=100"})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3, topics = {"screening.client-events.v1", "restriction.client-events.v1"})
class SaveFailureIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired OnboardingCommandHandler commands;
    @Autowired OnboardingQueries queries;
    @Autowired JdbcClient jdbc;
    @Autowired DataSource dataSource;
    @MockitoSpyBean JdbcEventStore eventStore;
    @MockitoSpyBean OnboardingIntegrationEvents integrationEvents;

    /**
     * Approving a manual review appends two events (v4 and v5). Another writer commits v5 first, so our v4 goes in
     * and our v5 collides. The handler answers with a value, not an exception - and Spring would happily COMMIT a
     * method that returns normally, leaving v4 behind. The handler therefore marks the transaction rollback-only.
     */
    @Test
    void lostRaceIsReportedAsAFailureAndTheEventWrittenBeforeTheCollisionIsRolledBack() {
        CustomerId id = start("Anna", "Konkurent");
        await().atMost(WAIT).untilAsserted(() -> assertThat(statusOf(id)).isEqualTo("MANUAL_REVIEW"));
        String stream = "customer-onboarding-" + id;
        assertThat(eventTypes(stream)).containsExactly("OnboardingStarted", "ApplicantVerified", "ManualReviewRequested");

        doAnswer(invocation -> {
            insertCommitted(stream, 5, "ConcurrentWriter");
            return invocation.callRealMethod();
        }).when(eventStore).append(eq(stream), eq(3L), anyList());

        Result<CustomerId, OnboardingError> result =
                commands.handle(new ApproveManualReviewCommand(id, "officer.anna", "Verified by phone"));

        assertThat(result).isEqualTo(Result.failure(new ConcurrentModification(id, 3)));
        assertThat(eventTypes(stream))
                .as("ManualReviewApproved (v4) was inserted before v5 collided and must have been rolled back")
                .containsExactly("OnboardingStarted", "ApplicantVerified", "ManualReviewRequested", "ConcurrentWriter");
    }

    /**
     * The step after the event store insert (preparing the outbox messages) fails. This is not a business outcome but
     * an infrastructure failure: it surfaces as an exception, and the transaction rolls back events, projections and
     * outbox messages together.
     */
    @Test
    void infrastructureFailureSurfacesAsAnExceptionAndRollsBackTheEvents() {
        AtomicReference<String> customer = new AtomicReference<>();
        doAnswer(invocation -> {
            customer.set(invocation.<CustomerOnboarding>getArgument(0).customerId().toString());
            throw new DataAccessResourceFailureException("outbox table unavailable");
        }).when(integrationEvents).toOutboxMessages(any(), anyList());

        assertThatThrownBy(() -> commands.handle(
                new StartOnboardingCommand("Jan", "Nowicki", "jan@example.com", LocalDate.parse("1990-01-01"))))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(customer.get()).as("the outbox insert was reached, after the events were written").isNotNull();
        assertThat(eventTypes("customer-onboarding-" + customer.get())).isEmpty();
    }

    private CustomerId start(String firstName, String lastName) {
        return switch (commands.handle(new StartOnboardingCommand(
                firstName, lastName, firstName + "@example.com", LocalDate.parse("1990-01-01")))) {
            case Result.Success(var id) -> id;
            case Result.Failure(var error) -> fail("start refused: " + error.message());
        };
    }

    private String statusOf(CustomerId id) {
        return switch (queries.get(id)) {
            case Result.Success(var view) -> view.status();
            case Result.Failure(var error) -> error.message();
        };
    }

    private List<String> eventTypes(String stream) {
        return jdbc.sql("SELECT event_type FROM event_store WHERE stream_id = :stream ORDER BY stream_version")
                .param("stream", stream)
                .query(String.class)
                .list();
    }

    /** Another connection, autocommit: this is what a concurrent writer in another transaction looks like. */
    private void insertCommitted(String stream, long version, String type) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO event_store (stream_id, stream_version, event_type, payload, occurred_at) "
                             + "VALUES (?, ?, ?, '{}', CURRENT_TIMESTAMP)")) {
            statement.setString(1, stream);
            statement.setLong(2, version);
            statement.setString(3, type);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
