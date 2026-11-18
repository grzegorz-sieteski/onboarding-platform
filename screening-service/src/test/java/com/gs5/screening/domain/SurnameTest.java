package com.gs5.screening.domain;

import com.gs5.screening.domain.ScreeningError.InvalidInput;
import com.gs5.screening.domain.ScreeningError.InvalidValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static com.gs5.screening.domain.ScreeningCaseTest.failureOf;
import static com.gs5.screening.domain.ScreeningCaseTest.valueOf;
import static org.assertj.core.api.Assertions.assertThat;

class SurnameTest {

    @ParameterizedTest
    @CsvSource({
            "Kowalski,   kowalski",
            "'  KOWALSKI ', kowalski",
            "Michałowski, michalowski",
            "Żółć,       zolc",
            "Nowak-Wąs,  nowak-was",
    })
    void normalizesForMatching(String raw, String expected) {
        assertThat(valueOf(Surname.of(raw)).normalized()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"Michałowski, MICHALOWSKI", "Wąs, Was"})
    void matchesRegardlessOfCaseAndDiacritics(String first, String second) {
        assertThat(valueOf(Surname.of(first)).matches(valueOf(Surname.of(second)))).isTrue();
    }

    @Test
    void refusesBlankSurname() {
        assertThat(failureOf(Surname.of(" "))).isInstanceOf(InvalidValue.class);
        assertThat(failureOf(Surname.of(null))).isInstanceOf(InvalidValue.class);
    }

    @Test
    void blacklistEntryNeedsSurnameAndReason() {
        assertThat(failureOf(BlacklistEntry.of("", "Fraud"))).isInstanceOf(InvalidValue.class);
        assertThat(failureOf(BlacklistEntry.of("Kowalski", " "))).isInstanceOf(InvalidValue.class);
        assertThat(valueOf(BlacklistEntry.of(" Kowalski ", "Fraud")))
                .isEqualTo(new BlacklistEntry(new Surname("Kowalski"), "Fraud"));
    }

    @Test
    void screenedPersonNeedsFirstNameAndSurname() {
        assertThat(failureOf(ScreenedPerson.of(" ", "Nowak"))).isInstanceOf(InvalidValue.class);
        assertThat(failureOf(ScreenedPerson.of("Jan", null))).isInstanceOf(InvalidValue.class);
    }

    @Test
    void blacklistEntryReportsEveryMissingPartAtOnce() {
        assertThat(failureOf(BlacklistEntry.of(" ", null))).isInstanceOfSatisfying(InvalidInput.class, input ->
                assertThat(input.problems().stream().map(ScreeningError::message).toList())
                        .containsExactly("Surname must not be blank", "Blacklist entry requires a reason"));
    }

    @Test
    void screenedPersonReportsEveryMissingPartAtOnce() {
        assertThat(failureOf(ScreenedPerson.of(null, ""))).isInstanceOfSatisfying(InvalidInput.class, input ->
                assertThat(input.problems().stream().map(ScreeningError::message).toList())
                        .containsExactly("Screened person requires a first name", "Surname must not be blank"));
    }

    @Test
    void parsesCustomerId() {
        UUID id = UUID.randomUUID();

        assertThat(valueOf(CustomerId.of(id.toString()))).isEqualTo(new CustomerId(id));
        assertThat(failureOf(CustomerId.of("not-a-uuid"))).isInstanceOf(InvalidValue.class);
        assertThat(failureOf(CustomerId.of(null))).isInstanceOf(InvalidValue.class);
    }
}
