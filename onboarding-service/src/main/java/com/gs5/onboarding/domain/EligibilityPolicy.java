package com.gs5.onboarding.domain;

import com.gs5.onboarding.domain.EligibilityDecision.Eligible;
import com.gs5.onboarding.domain.EligibilityDecision.Ineligible;

import java.time.LocalDate;
import java.time.Period;

/** Business rules deciding whether the submitted data qualifies the applicant to become a customer. */
public record EligibilityPolicy(int minimumAge) {

    public static final int DEFAULT_MINIMUM_AGE = 18;

    public EligibilityPolicy() {
        this(DEFAULT_MINIMUM_AGE);
    }

    public EligibilityDecision evaluate(Applicant applicant, LocalDate today) {
        LocalDate dateOfBirth = applicant.dateOfBirth();
        if (dateOfBirth.isAfter(today)) {
            return new Ineligible("Date of birth lies in the future");
        }
        if (Period.between(dateOfBirth, today).getYears() < minimumAge) {
            return new Ineligible("Applicant must be at least " + minimumAge + " years old");
        }
        return new Eligible();
    }
}
