package com.gs5.screening.domain;

import com.gs5.screening.domain.shared.InvalidValueException;

public record BlacklistEntry(Surname surname, String reason) {

    public BlacklistEntry {
        if (surname == null) {
            throw new InvalidValueException("Blacklist entry requires a surname");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidValueException("Blacklist entry requires a reason");
        }
    }
}
