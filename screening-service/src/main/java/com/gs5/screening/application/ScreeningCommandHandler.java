package com.gs5.screening.application;

import com.gs5.screening.domain.Blacklist;
import com.gs5.screening.domain.CustomerId;
import com.gs5.screening.domain.ScreenedPerson;
import com.gs5.screening.domain.ScreeningCase;
import com.gs5.screening.domain.ScreeningCaseRepository;
import com.gs5.screening.domain.ScreeningError;
import com.gs5.screening.domain.shared.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.Clock;

/**
 * Application service. A command answers with a {@link Result}. A command rejected by the domain writes nothing.
 * A failed {@code save} may have written part of its work, and a transaction only rolls back on its own for an
 * exception, so a failure is rolled back explicitly in {@link #persist(ScreeningCase)}.
 */
@Service
public class ScreeningCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(ScreeningCommandHandler.class);

    private final ScreeningCaseRepository screenings;
    private final Blacklist blacklist;
    private final Clock clock;

    public ScreeningCommandHandler(ScreeningCaseRepository screenings, Blacklist blacklist, Clock clock) {
        this.screenings = screenings;
        this.blacklist = blacklist;
        this.clock = clock;
    }

    /** A customer is screened once - a replayed {@code ApplicantVerified} must not open a second case. */
    @Transactional
    public Result<CustomerId, ScreeningError> handle(ScreenCustomerCommand command) {
        if (screenings.exists(command.customerId())) {
            log.info("Customer {} already screened, skipping", command.customerId());
            return Result.success(command.customerId());
        }
        return ScreenedPerson.of(command.firstName(), command.lastName())
                .map(person -> ScreeningCase.screen(command.customerId(), person, blacklist, clock))
                .flatMap(this::persist);
    }

    private Result<CustomerId, ScreeningError> persist(ScreeningCase screening) {
        return switch (screenings.save(screening)) {
            case Result.Success(var saved) -> Result.success(saved.customerId());
            case Result.Failure(var error) -> {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                yield Result.failure(error);
            }
        };
    }
}
