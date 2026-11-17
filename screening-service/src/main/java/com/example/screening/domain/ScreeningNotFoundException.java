package com.example.screening.domain;

import com.example.screening.domain.shared.DomainException;

public class ScreeningNotFoundException extends DomainException {

    public ScreeningNotFoundException(CustomerId customerId) {
        super("No screening found for customer " + customerId);
    }
}
