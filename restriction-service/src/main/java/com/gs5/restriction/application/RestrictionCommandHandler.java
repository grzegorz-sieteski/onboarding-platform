package com.gs5.restriction.application;

import com.gs5.restriction.domain.ClientRestriction;
import com.gs5.restriction.domain.ClientRestrictionRepository;
import com.gs5.restriction.domain.CustomerId;
import com.gs5.restriction.domain.RestrictionError;
import com.gs5.restriction.domain.shared.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.Clock;

/**
 * Application service. A command answers with a {@link Result}. A command rejected by the domain writes nothing.
 * A failed {@code save} may have written part of its work, and a transaction only rolls back on its own for an
 * exception, so a failure is rolled back explicitly in {@link #persist(ClientRestriction)}.
 */
@Service
public class RestrictionCommandHandler {

    private final ClientRestrictionRepository restrictions;
    private final Clock clock;

    public RestrictionCommandHandler(ClientRestrictionRepository restrictions, Clock clock) {
        this.restrictions = restrictions;
        this.clock = clock;
    }

    @Transactional
    public Result<CustomerId, RestrictionError> handle(BanClientCommand command) {
        return restrictions.load(command.customerId())
                .flatMap(restriction -> restriction.ban(command.reason(), command.source(), clock))
                .flatMap(this::persist);
    }

    private Result<CustomerId, RestrictionError> persist(ClientRestriction restriction) {
        return switch (restrictions.save(restriction)) {
            case Result.Success(var saved) -> Result.success(saved.customerId());
            case Result.Failure(var error) -> {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                yield Result.failure(error);
            }
        };
    }
}
