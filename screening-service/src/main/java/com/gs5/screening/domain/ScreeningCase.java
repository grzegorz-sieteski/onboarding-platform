package com.gs5.screening.domain;

import com.gs5.screening.domain.ScreeningEvent.BlacklistMatchFound;
import com.gs5.screening.domain.ScreeningEvent.ScreeningPassed;
import com.gs5.screening.domain.ScreeningEvent.ScreeningRequested;
import com.gs5.screening.domain.shared.EventSourcedAggregate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Aggregate root: the compliance screening of one customer. Its event stream is the audit trail of
 * what was checked, when, and against which blacklist entry - which is exactly why it is event sourced.
 */
public final class ScreeningCase extends EventSourcedAggregate<ScreeningEvent> {

    private CustomerId customerId;
    private ScreenedPerson person;
    private ScreeningStatus status;
    private String restrictionReason;

    private ScreeningCase() {
    }

    public static ScreeningCase screen(CustomerId customerId, ScreenedPerson person, Blacklist blacklist,
                                       Clock clock) {
        var screening = new ScreeningCase();
        Instant now = clock.instant();
        screening.raise(new ScreeningRequested(customerId.value(), person.firstName(), person.surname().value(), now));
        blacklist.findEntryMatching(person.surname()).ifPresentOrElse(
                entry -> screening.raise(new BlacklistMatchFound(customerId.value(), entry.surname().value(),
                        entry.reason(), now)),
                () -> screening.raise(new ScreeningPassed(customerId.value(), now)));
        return screening;
    }

    /** An empty history means there is no such screening. */
    public static Optional<ScreeningCase> rehydrate(List<ScreeningEvent> history) {
        if (history.isEmpty()) {
            return Optional.empty();
        }
        var screening = new ScreeningCase();
        screening.replay(history);
        return Optional.of(screening);
    }

    /** Replays trusted history: events were validated when they were raised, so no validation happens here. */
    @Override
    protected void apply(ScreeningEvent event) {
        switch (event) {
            case ScreeningRequested requested -> {
                customerId = new CustomerId(requested.customerId());
                person = new ScreenedPerson(requested.firstName(), new Surname(requested.lastName()));
                status = ScreeningStatus.IN_PROGRESS;
            }
            case ScreeningPassed _ -> status = ScreeningStatus.CLEARED;
            case BlacklistMatchFound match -> {
                status = ScreeningStatus.RESTRICTED;
                restrictionReason = match.reason();
            }
        }
    }

    public CustomerId customerId() {
        return customerId;
    }

    public ScreenedPerson person() {
        return person;
    }

    public ScreeningStatus status() {
        return status;
    }

    public Optional<String> restrictionReason() {
        return Optional.ofNullable(restrictionReason);
    }
}
