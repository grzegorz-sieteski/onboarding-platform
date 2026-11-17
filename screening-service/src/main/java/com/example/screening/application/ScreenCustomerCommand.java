package com.example.screening.application;

import com.example.screening.domain.CustomerId;

public record ScreenCustomerCommand(CustomerId customerId, String firstName, String lastName) {
}
