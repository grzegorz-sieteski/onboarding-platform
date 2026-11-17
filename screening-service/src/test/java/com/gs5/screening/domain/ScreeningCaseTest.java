package com.gs5.screening.domain;

import com.gs5.screening.domain.ScreeningEvent.BlacklistMatchFound;
import com.gs5.screening.domain.ScreeningEvent.ScreeningPassed;
import com.gs5.screening.domain.ScreeningEvent.ScreeningRequested;
import com.gs5.screening.domain.shared.Result;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class ScreeningCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);

    private final InMemoryBlacklist blacklist = new InMemoryBlacklist();

    @Test
    void clearsCustomerWhoseSurnameIsNotBlacklisted() {
        ScreeningCase screening = screen("Jan", "Nowak");

        assertThat(screening.uncommittedEvents())
                .hasExactlyElementsOfTypes(ScreeningRequested.class, ScreeningPassed.class);
        assertThat(screening.status()).isEqualTo(ScreeningStatus.CLEARED);
    }

    @Test
    void restrictsCustomerWhoseSurnameMatchesBlacklistEntry() {
        blacklist.add(valueOf(BlacklistEntry.of("Michałowski", "Sanctions list EU-2026/17")));

        ScreeningCase screening = screen("Adam", "MICHALOWSKI");

        assertThat(screening.uncommittedEvents())
                .hasExactlyElementsOfTypes(ScreeningRequested.class, BlacklistMatchFound.class);
        assertThat(screening.status()).isEqualTo(ScreeningStatus.RESTRICTED);
        assertThat(screening.restrictionReason()).hasValue("Sanctions list EU-2026/17");
    }

    @Test
    void rehydratesFromHistory() {
        blacklist.add(valueOf(BlacklistEntry.of("Kowalski", "Fraud")));
        List<ScreeningEvent> history = screen("Jan", "Kowalski").uncommittedEvents();

        assertThat(ScreeningCase.rehydrate(history)).hasValueSatisfying(rehydrated -> {
            assertThat(rehydrated.status()).isEqualTo(ScreeningStatus.RESTRICTED);
            assertThat(rehydrated.version()).isEqualTo(2);
        });
    }

    @Test
    void emptyHistoryMeansThereIsNoScreening() {
        assertThat(ScreeningCase.rehydrate(List.of())).isEmpty();
    }

    private ScreeningCase screen(String firstName, String lastName) {
        return ScreeningCase.screen(CustomerId.newId(), valueOf(ScreenedPerson.of(firstName, lastName)),
                blacklist, CLOCK);
    }

    static <T> T valueOf(Result<T, ScreeningError> result) {
        return switch (result) {
            case Result.Success<T, ScreeningError>(var value) -> value;
            case Result.Failure<T, ScreeningError>(var error) -> fail("expected a value but got " + error);
        };
    }

    static <T> ScreeningError failureOf(Result<T, ScreeningError> result) {
        return switch (result) {
            case Result.Failure<T, ScreeningError>(var error) -> error;
            case Result.Success<T, ScreeningError>(var value) -> fail("expected a failure but got " + value);
        };
    }

    private static final class InMemoryBlacklist implements Blacklist {

        private final List<BlacklistEntry> entries = new ArrayList<>();

        @Override
        public Optional<BlacklistEntry> findEntryMatching(Surname surname) {
            return entries.stream().filter(entry -> entry.surname().matches(surname)).findFirst();
        }

        @Override
        public void add(BlacklistEntry entry) {
            entries.add(entry);
        }

        @Override
        public void remove(Surname surname) {
            entries.removeIf(entry -> entry.surname().matches(surname));
        }

        @Override
        public List<BlacklistEntry> entries() {
            return List.copyOf(entries);
        }
    }
}
