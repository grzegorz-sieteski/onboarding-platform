package com.gs5.restriction.application;

import com.gs5.restriction.domain.ClientRestriction;
import com.gs5.restriction.domain.ClientRestrictionRepository;
import com.gs5.restriction.domain.CustomerId;
import com.gs5.restriction.domain.RestrictionError;
import com.gs5.restriction.domain.shared.Result;

import java.time.Clock;

/**
 * Application service: bans clients. Knows no framework; the command runs in one {@link UnitOfWork}, so a failure
 * writes nothing.
 */
public class RestrictionCommandHandler {

    private final ClientRestrictionRepository restrictions;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RestrictionCommandHandler(ClientRestrictionRepository restrictions, UnitOfWork unitOfWork, Clock clock) {
        this.restrictions = restrictions;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public Result<CustomerId, RestrictionError> handle(BanClientCommand command) {
        return unitOfWork.inTransaction(() -> restrictions.load(command.customerId())
                .flatMap(restriction -> restriction.ban(command.reason(), command.source(), clock))
                .flatMap(restrictions::save)
                .map(ClientRestriction::customerId));
    }
}
