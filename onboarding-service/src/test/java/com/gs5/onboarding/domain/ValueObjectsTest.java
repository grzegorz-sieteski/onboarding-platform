package com.gs5.onboarding.domain;

import com.gs5.onboarding.domain.OnboardingError.InvalidValue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static com.gs5.onboarding.domain.CustomerOnboardingTest.failureOf;
import static com.gs5.onboarding.domain.CustomerOnboardingTest.valueOf;
import static org.assertj.core.api.Assertions.assertThat;

/** Value objects validate untrusted input in their factories and answer with a {@code Result}, never with an exception. */
class ValueObjectsTest {

    @Test
    void refusesInvalidPersonName() {
        assertThat(failureOf(PersonName.of("Jan", "N0wak!"))).isInstanceOf(InvalidValue.class);
        assertThat(failureOf(PersonName.of(" ", "Nowak"))).isInstanceOf(InvalidValue.class);
        assertThat(failureOf(PersonName.of("Jan", null))).isInstanceOf(InvalidValue.class);
    }

    @Test
    void stripsPersonName() {
        assertThat(valueOf(PersonName.of("  Jan ", "Nowak-Kowalski"))).isEqualTo(new PersonName("Jan", "Nowak-Kowalski"));
    }

    @Test
    void normalizesEmailAddress() {
        assertThat(valueOf(EmailAddress.of("  Jan@Example.COM "))).isEqualTo(new EmailAddress("jan@example.com"));
    }

    @Test
    void refusesInvalidEmailAddress() {
        assertThat(failureOf(EmailAddress.of("not-an-email"))).isInstanceOf(InvalidValue.class);
        assertThat(failureOf(EmailAddress.of(null))).isInstanceOf(InvalidValue.class);
    }

    @Test
    void parsesCustomerId() {
        UUID id = UUID.randomUUID();

        assertThat(valueOf(CustomerId.of(id.toString()))).isEqualTo(new CustomerId(id));
    }

    @Test
    void refusesMalformedCustomerId() {
        assertThat(failureOf(CustomerId.of("not-a-uuid"))).isInstanceOf(InvalidValue.class);
        assertThat(failureOf(CustomerId.of(""))).isInstanceOf(InvalidValue.class);
        assertThat(failureOf(CustomerId.of(null))).isInstanceOf(InvalidValue.class);
    }

    @Test
    void refusesNonPositiveScreeningTimeout() {
        assertThat(failureOf(ScreeningSla.of(Duration.ZERO))).isInstanceOf(InvalidValue.class);
        assertThat(failureOf(ScreeningSla.of(Duration.ofMinutes(-1)))).isInstanceOf(InvalidValue.class);
        assertThat(failureOf(ScreeningSla.of(null))).isInstanceOf(InvalidValue.class);
        assertThat(valueOf(ScreeningSla.of(Duration.ofMinutes(15)))).isEqualTo(new ScreeningSla(Duration.ofMinutes(15)));
    }

    @Test
    void applicantNeedsEveryPart() {
        assertThat(failureOf(Applicant.of("Jan", "Nowak", "jan@example.com", null))).isInstanceOf(InvalidValue.class);
        assertThat(failureOf(Applicant.of("Jan", "N0wak", "jan@example.com", LocalDate.parse("1990-01-01"))))
                .isInstanceOf(InvalidValue.class);
        assertThat(failureOf(Applicant.of("Jan", "Nowak", "nope", LocalDate.parse("1990-01-01"))))
                .isInstanceOf(InvalidValue.class);
    }
}
