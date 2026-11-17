package com.gs5.screening.domain;

import com.gs5.screening.domain.shared.DomainException;

public class ScreeningNotFoundException extends DomainException {

    public ScreeningNotFoundException(CustomerId customerId) {
        super("No screening found for customer " + customerId);
    }
}
