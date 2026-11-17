package com.gs5.screening.domain;

import com.gs5.screening.domain.shared.Result;

public interface ScreeningCaseRepository {

    /**
     * Rebuilds the aggregate from its history. Fails with {@code ScreeningNotFound} when the customer has no
     * screening and with {@code CorruptedHistory} when a stored event cannot be read back.
     */
    Result<ScreeningCase, ScreeningError> load(CustomerId customerId);

    boolean exists(CustomerId customerId);

    /**
     * Persists the uncommitted events atomically with their outbox messages. A failure means nothing was written;
     * the caller must then roll its transaction back (the database may have aborted it).
     */
    Result<ScreeningCase, ScreeningError> save(ScreeningCase screeningCase);
}
