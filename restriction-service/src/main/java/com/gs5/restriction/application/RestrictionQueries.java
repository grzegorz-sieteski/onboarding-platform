package com.gs5.restriction.application;

import com.gs5.restriction.domain.ClientRestrictionRepository;
import com.gs5.restriction.domain.CustomerId;
import com.gs5.restriction.domain.RestrictionError;
import com.gs5.restriction.domain.shared.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestrictionQueries {

    private final ClientRestrictionRepository restrictions;

    public RestrictionQueries(ClientRestrictionRepository restrictions) {
        this.restrictions = restrictions;
    }

    @Transactional(readOnly = true)
    public Result<RestrictionView, RestrictionError> get(CustomerId customerId) {
        return restrictions.load(customerId).map(RestrictionView::of);
    }
}
