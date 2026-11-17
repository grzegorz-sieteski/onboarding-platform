package com.example.restriction.application;

import com.example.restriction.domain.CustomerId;
import com.example.restriction.domain.RestrictionSource;

public record BanClientCommand(CustomerId customerId, String reason, RestrictionSource source) {
}
