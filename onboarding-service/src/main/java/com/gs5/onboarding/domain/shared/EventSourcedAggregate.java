package com.gs5.onboarding.domain.shared;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for aggregates whose state is derived exclusively from their own event stream.
 * <p>
 * Every state change goes through {@link #raise(DomainEvent)}, which applies the event and records it as uncommitted.
 * Rehydration replays the history through the very same {@link #apply(DomainEvent)} method, so there is exactly one
 * place where state transitions are defined.
 */
public abstract class EventSourcedAggregate<E extends DomainEvent> {

    private final List<E> uncommittedEvents = new ArrayList<>();
    private long version;

    protected abstract void apply(E event);

    protected final void raise(E event) {
        apply(event);
        uncommittedEvents.add(event);
    }

    protected final void replay(List<? extends E> history) {
        for (E event : history) {
            apply(event);
            version++;
        }
    }

    /** Version of the last persisted event; {@code 0} for a brand-new stream. Used for optimistic concurrency. */
    public final long version() {
        return version;
    }

    public final List<E> uncommittedEvents() {
        return List.copyOf(uncommittedEvents);
    }

    public final void markEventsAsCommitted() {
        version += uncommittedEvents.size();
        uncommittedEvents.clear();
    }
}
