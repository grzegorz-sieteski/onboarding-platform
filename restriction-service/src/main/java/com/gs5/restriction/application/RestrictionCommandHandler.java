package com.gs5.restriction.application;

import com.gs5.restriction.domain.ClientRestriction;
import com.gs5.restriction.domain.ClientRestrictionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class RestrictionCommandHandler {

    private final ClientRestrictionRepository restrictions;
    private final Clock clock;

    public RestrictionCommandHandler(ClientRestrictionRepository restrictions, Clock clock) {
        this.restrictions = restrictions;
        this.clock = clock;
    }

    @Transactional
    public void handle(BanClientCommand command) {
        ClientRestriction restriction = restrictions.findById(command.customerId());
        restriction.ban(command.reason(), command.source(), clock);
        restrictions.save(restriction);
    }
}
