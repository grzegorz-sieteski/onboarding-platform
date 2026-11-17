package com.gs5.screening.domain;

import com.gs5.screening.domain.ScreeningError.InvalidValue;
import com.gs5.screening.domain.shared.Result;

public record BlacklistEntry(Surname surname, String reason) {

    /** Entry point for untrusted input. The constructor is for already valid values. */
    public static Result<BlacklistEntry, ScreeningError> of(String surname, String reason) {
        return Surname.of(surname).flatMap(parsed -> withReason(parsed, reason));
    }

    private static Result<BlacklistEntry, ScreeningError> withReason(Surname surname, String reason) {
        if (reason == null || reason.isBlank()) {
            return Result.failure(new InvalidValue("Blacklist entry requires a reason"));
        }
        return Result.success(new BlacklistEntry(surname, reason));
    }
}
