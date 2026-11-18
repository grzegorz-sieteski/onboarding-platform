package com.gs5.screening.infrastructure.persistence;

import com.gs5.screening.application.UnitOfWork;
import com.gs5.screening.domain.shared.Result;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * {@link UnitOfWork} on programmatic transactions. The one rule that matters lives here: a use case that returns a
 * {@link Result.Failure} is rolled back. Without it, a method that returns normally would be committed together with
 * whatever it wrote before it failed. An exception rolls back too, and is passed on.
 */
public final class TransactionTemplateUnitOfWork implements UnitOfWork {

    private final TransactionTemplate writes;
    private final TransactionTemplate reads;

    public TransactionTemplateUnitOfWork(PlatformTransactionManager transactionManager) {
        this.writes = new TransactionTemplate(transactionManager);
        this.reads = new TransactionTemplate(transactionManager);
        this.reads.setReadOnly(true);
    }

    @Override
    public <T, E> Result<T, E> inTransaction(Supplier<Result<T, E>> work) {
        return writes.execute(status -> {
            Result<T, E> result = work.get();
            if (result instanceof Result.Failure<T, E>) {
                status.setRollbackOnly();
            }
            return result;
        });
    }

    @Override
    public <T> T readOnly(Supplier<T> query) {
        return reads.execute(_ -> query.get());
    }
}
