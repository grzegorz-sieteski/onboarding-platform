package com.example.screening.application;

import com.example.screening.domain.ScreeningCase;

public record ScreeningView(String customerId, String firstName, String lastName, String status,
                            String restrictionReason) {

    static ScreeningView of(ScreeningCase screening) {
        return new ScreeningView(
                screening.customerId().toString(),
                screening.person().firstName(),
                screening.person().surname().value(),
                screening.status().name(),
                screening.restrictionReason().orElse(null));
    }
}
