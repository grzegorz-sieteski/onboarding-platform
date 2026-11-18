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

import java.time.Clock;

/**
 * Application service: screens a verified applicant against the blacklist. Knows no framework; the command runs in
 * one {@link UnitOfWork}, so a failure writes nothing.
 */
public class ScreeningCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(ScreeningCommandHandler.class);

    private final ScreeningCaseRepository screenings;
    private final Blacklist blacklist;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ScreeningCommandHandler(ScreeningCaseRepository screenings, Blacklist blacklist, UnitOfWork unitOfWork,
                                   Clock clock) {
        this.screenings = screenings;
        this.blacklist = blacklist;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /** A customer is screened once - a replayed {@code ApplicantVerified} must not open a second case. */
    public Result<CustomerId, ScreeningError> handle(ScreenCustomerCommand command) {
        return unitOfWork.inTransaction(() -> {
            if (screenings.exists(command.customerId())) {
                log.info("Customer {} already screened, skipping", command.customerId());
                return Result.success(command.customerId());
            }
            return ScreenedPerson.of(command.firstName(), command.lastName())
                    .map(person -> ScreeningCase.screen(command.customerId(), person, blacklist, clock))
                    .flatMap(screenings::save)
                    .map(ScreeningCase::customerId);
        });
    }
}
