package com.gs5.restriction.application;

import com.gs5.restriction.domain.CustomerId;
import com.gs5.restriction.domain.RestrictionSource;

public record BanClientCommand(CustomerId customerId, String reason, RestrictionSource source) {
}
