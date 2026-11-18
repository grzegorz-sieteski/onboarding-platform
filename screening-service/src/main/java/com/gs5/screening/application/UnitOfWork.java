package com.gs5.screening.application;

import com.gs5.screening.domain.shared.Result;

import java.util.function.Supplier;

/**
 * Port: the transaction boundary of a use case. The application decides what has to happen atomically, the
 * infrastructure decides how. A use case that ends in a {@link Result.Failure} leaves nothing behind - whatever it
 * managed to write is rolled back - so a failure never has to be undone by hand.
 */
public interface UnitOfWork {

    /** Runs the work atomically. A {@code Failure} rolls back everything the work wrote. */
    <T, E> Result<T, E> inTransaction(Supplier<Result<T, E>> work);

    /** Runs a query against one consistent, read-only view of the data. */
    <T> T readOnly(Supplier<T> query);
}
