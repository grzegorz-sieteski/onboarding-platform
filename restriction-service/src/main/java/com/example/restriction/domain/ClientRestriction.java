package com.example.restriction.domain;

import com.example.restriction.domain.RestrictionEvent.BanRequestIgnored;
import com.example.restriction.domain.RestrictionEvent.ClientBanned;
import com.example.restriction.domain.shared.EventSourcedAggregate;
import com.example.restriction.domain.shared.InvalidValueException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Aggregate root: the restriction status of one client - the single source of truth for "may this client
 * do business with us". Invariant: a client is banned at most once; subsequent ban requests (e.g. from another
 * source) are recorded for audit but do not produce a second {@link ClientBanned}.
 */
public final class ClientRestriction extends EventSourcedAggregate<RestrictionEvent> {

    private CustomerId customerId;
    private boolean banned;
    private String banReason;
    private RestrictionSource banSource;
    private Instant bannedAt;

    private ClientRestriction(CustomerId customerId) {
        this.customerId = customerId;
    }

    /** A client nobody has restricted yet - an empty stream is a perfectly valid state. */
    public static ClientRestriction unrestricted(CustomerId customerId) {
        return new ClientRestriction(customerId);
    }

    public static ClientRestriction rehydrate(CustomerId customerId, List<RestrictionEvent> history) {
        var restriction = new ClientRestriction(customerId);
        restriction.replay(history);
        return restriction;
    }

    public void ban(String reason, RestrictionSource source, Clock clock) {
        if (reason == null || reason.isBlank() || source == null) {
            throw new InvalidValueException("A ban requires a reason and a source");
        }
        if (banned) {
            raise(new BanRequestIgnored(customerId.value(), reason, source, clock.instant()));
            return;
        }
        raise(new ClientBanned(customerId.value(), reason, source, clock.instant()));
    }

    @Override
    protected void apply(RestrictionEvent event) {
        switch (event) {
            case ClientBanned clientBanned -> {
                banned = true;
                banReason = clientBanned.reason();
                banSource = clientBanned.source();
                bannedAt = clientBanned.occurredAt();
            }
            case BanRequestIgnored ignored -> {
                // audit only - state does not change
            }
        }
    }

    public CustomerId customerId() {
        return customerId;
    }

    public boolean isBanned() {
        return banned;
    }

    public Optional<String> banReason() {
        return Optional.ofNullable(banReason);
    }

    public Optional<RestrictionSource> banSource() {
        return Optional.ofNullable(banSource);
    }

    public Optional<Instant> bannedAt() {
        return Optional.ofNullable(bannedAt);
    }
}
