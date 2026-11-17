package com.example.restriction.domain;

import com.example.restriction.domain.RestrictionEvent.BanRequestIgnored;
import com.example.restriction.domain.RestrictionEvent.ClientBanned;
import com.example.restriction.domain.shared.InvalidValueException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        restriction.ban("Surname on blacklist", RestrictionSource.SANCTIONS_SCREENING, CLOCK);

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
    void rejectsBanWithoutReason() {
        assertThatThrownBy(() -> ClientRestriction.unrestricted(CUSTOMER_ID)
                .ban(" ", RestrictionSource.MANUAL, CLOCK))
                .isInstanceOf(InvalidValueException.class);
    }
}
