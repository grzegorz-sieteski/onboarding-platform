package com.gs5.restriction.domain;

import com.gs5.restriction.domain.RestrictionError.InvalidInput;
import com.gs5.restriction.domain.RestrictionError.InvalidValue;
import com.gs5.restriction.domain.RestrictionEvent.BanRequestIgnored;
import com.gs5.restriction.domain.RestrictionEvent.ClientBanned;
import com.gs5.restriction.domain.shared.Result;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class ClientRestrictionTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
    private static final CustomerId CUSTOMER_ID = CustomerId.newId();

    @Test
    void newClientIsNotBanned() {
        assertThat(ClientRestriction.unrestricted(CUSTOMER_ID).isBanned()).isFalse();
    }

    @Test
    void bansClient() {
        var restriction = ClientRestriction.unrestricted(CUSTOMER_ID);

        Result<ClientRestriction, RestrictionError> result =
                restriction.ban("Surname on blacklist", RestrictionSource.SANCTIONS_SCREENING, CLOCK);

        assertThat(valueOf(result)).isSameAs(restriction);
        assertThat(restriction.uncommittedEvents()).singleElement().isInstanceOf(ClientBanned.class);
        assertThat(restriction.isBanned()).isTrue();
        assertThat(restriction.banSource()).hasValue(RestrictionSource.SANCTIONS_SCREENING);
    }

    @Test
    void secondBanIsRecordedForAuditButDoesNotBanAgain() {
        var restriction = ClientRestriction.unrestricted(CUSTOMER_ID);
        restriction.ban("Surname on blacklist", RestrictionSource.SANCTIONS_SCREENING, CLOCK);
        restriction.markEventsAsCommitted();

        restriction.ban("Suspicious activity", RestrictionSource.MANUAL, CLOCK);

        assertThat(restriction.uncommittedEvents()).singleElement().isInstanceOf(BanRequestIgnored.class);
        assertThat(restriction.banReason()).hasValue("Surname on blacklist");
    }

    @Test
    void rejectsBanWithoutReasonAndRaisesNothing() {
        var restriction = ClientRestriction.unrestricted(CUSTOMER_ID);

        assertThat(failureOf(restriction.ban(" ", RestrictionSource.MANUAL, CLOCK))).isInstanceOf(InvalidValue.class);
        assertThat(failureOf(restriction.ban("Fraud", null, CLOCK))).isInstanceOf(InvalidValue.class);

        assertThat(restriction.uncommittedEvents()).isEmpty();
        assertThat(restriction.isBanned()).isFalse();
    }

    @Test
    void reportsEveryMissingPartOfABanAtOnce() {
        var restriction = ClientRestriction.unrestricted(CUSTOMER_ID);

        assertThat(failureOf(restriction.ban(" ", null, CLOCK))).isInstanceOfSatisfying(InvalidInput.class, input ->
                assertThat(input.problems().stream().map(RestrictionError::message).toList())
                        .containsExactly("A ban requires a reason", "A ban requires a source"));
        assertThat(restriction.uncommittedEvents()).isEmpty();
    }

    @Test
    void rehydratesFromHistoryAndEmptyHistoryIsAnUnrestrictedClient() {
        UUID id = CUSTOMER_ID.value();
        var history = List.<RestrictionEvent>of(
                new ClientBanned(id, "Fraud", RestrictionSource.MANUAL, CLOCK.instant()));

        var banned = ClientRestriction.rehydrate(CUSTOMER_ID, history);
        var unrestricted = ClientRestriction.rehydrate(CUSTOMER_ID, List.of());

        assertThat(banned.isBanned()).isTrue();
        assertThat(banned.version()).isEqualTo(1);
        assertThat(unrestricted.isBanned()).isFalse();
        assertThat(unrestricted.version()).isZero();
    }

    @Test
    void parsesCustomerId() {
        UUID id = UUID.randomUUID();

        assertThat(valueOf(CustomerId.of(id.toString()))).isEqualTo(new CustomerId(id));
        assertThat(failureOf(CustomerId.of("not-a-uuid"))).isInstanceOf(InvalidValue.class);
        assertThat(failureOf(CustomerId.of(null))).isInstanceOf(InvalidValue.class);
    }

    private static <T> T valueOf(Result<T, RestrictionError> result) {
        return switch (result) {
            case Result.Success<T, RestrictionError>(var value) -> value;
            case Result.Failure<T, RestrictionError>(var error) -> fail("expected a value but got " + error);
        };
    }

    private static <T> RestrictionError failureOf(Result<T, RestrictionError> result) {
        return switch (result) {
            case Result.Failure<T, RestrictionError>(var error) -> error;
            case Result.Success<T, RestrictionError>(var value) -> fail("expected a failure but got " + value);
        };
    }
}
