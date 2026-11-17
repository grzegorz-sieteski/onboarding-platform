package com.gs5.screening.application;

import com.gs5.screening.domain.Blacklist;
import com.gs5.screening.domain.ScreenedPerson;
import com.gs5.screening.domain.ScreeningCase;
import com.gs5.screening.domain.ScreeningCaseRepository;
import com.gs5.screening.domain.Surname;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

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
    public void handle(ScreenCustomerCommand command) {
        if (screenings.exists(command.customerId())) {
            log.info("Customer {} already screened, skipping", command.customerId());
            return;
        }
        var person = new ScreenedPerson(command.firstName(), new Surname(command.lastName()));
        screenings.save(ScreeningCase.screen(command.customerId(), person, blacklist, clock));
    }
}
