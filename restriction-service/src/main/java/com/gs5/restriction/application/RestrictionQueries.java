package com.gs5.restriction.application;

import com.gs5.restriction.domain.ClientRestrictionRepository;
import com.gs5.restriction.domain.CustomerId;
import com.gs5.restriction.domain.RestrictionError;
import com.gs5.restriction.domain.shared.Result;

public class RestrictionQueries {

    private final ClientRestrictionRepository restrictions;
    private final UnitOfWork unitOfWork;

    public RestrictionQueries(ClientRestrictionRepository restrictions, UnitOfWork unitOfWork) {
        this.restrictions = restrictions;
        this.unitOfWork = unitOfWork;
    }

    public Result<RestrictionView, RestrictionError> get(CustomerId customerId) {
        return unitOfWork.readOnly(() -> restrictions.load(customerId).map(RestrictionView::of));
    }
}
