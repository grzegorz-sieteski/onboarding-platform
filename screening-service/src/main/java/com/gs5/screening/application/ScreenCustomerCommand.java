package com.gs5.screening.application;

import com.gs5.screening.domain.CustomerId;

public record ScreenCustomerCommand(CustomerId customerId, String firstName, String lastName) {
}
