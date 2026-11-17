package com.gs5.onboarding.domain;

import java.time.LocalDate;
import java.time.Period;

/** Business rules deciding whether the submitted data qualifies the applicant to become a customer. */
public class EligibilityPolicy {

    static final int MINIMUM_AGE = 18;

    public EligibilityDecision evaluate(Applicant applicant, LocalDate today) {
        LocalDate dateOfBirth = applicant.dateOfBirth();
        if (dateOfBirth.isAfter(today)) {
            return EligibilityDecision.rejected("Date of birth lies in the future");
        }
        if (Period.between(dateOfBirth, today).getYears() < MINIMUM_AGE) {
            return EligibilityDecision.rejected("Applicant must be at least " + MINIMUM_AGE + " years old");
        }
        return EligibilityDecision.approved();
    }
}
