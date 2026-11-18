package com.gs5.screening.domain;

import com.gs5.screening.domain.ScreeningError.InvalidInput;
import com.gs5.screening.domain.ScreeningError.InvalidValue;
import com.gs5.screening.domain.shared.Result;
import com.gs5.screening.domain.shared.Validation;

public record BlacklistEntry(Surname surname, String reason) {

    /** Entry point for untrusted input. The constructor is for already valid values. */
    public static Result<BlacklistEntry, ScreeningError> of(String surname, String reason) {
        var validation = Validation.of(InvalidInput::new);
        var validSurname = validation.check(Surname.of(surname));
        var validReason = validation.check(validReason(reason));
        return validation.build(() -> new BlacklistEntry(validSurname.value(), validReason.value()));
    }

    private static Result<String, ScreeningError> validReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return Result.failure(new InvalidValue("Blacklist entry requires a reason"));
        }
        return Result.success(reason);
    }
}
