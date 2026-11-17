package com.gs5.restriction.application;

import com.gs5.restriction.domain.ClientRestriction;

import java.time.Instant;

public record RestrictionView(String customerId, boolean banned, String reason, String source, Instant bannedAt) {

    static RestrictionView of(ClientRestriction restriction) {
        return new RestrictionView(
                restriction.customerId().toString(),
                restriction.isBanned(),
                restriction.banReason().orElse(null),
                restriction.banSource().map(Enum::name).orElse(null),
                restriction.bannedAt().orElse(null));
    }
}
