package com.gs5.restriction.domain;

import com.gs5.restriction.domain.shared.Result;

public interface ClientRestrictionRepository {

    /**
     * Rebuilds the aggregate from its history. Never "not found": a client without history is simply unrestricted.
     * Fails with {@code CorruptedHistory} when a stored event cannot be read back.
     */
    Result<ClientRestriction, RestrictionError> load(CustomerId customerId);

    /**
     * Persists the uncommitted events atomically with their outbox messages. A failure means nothing was written;
     * the caller must then roll its transaction back (the database may have aborted it).
     */
    Result<ClientRestriction, RestrictionError> save(ClientRestriction restriction);
}
