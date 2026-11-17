package com.example.restriction.application;

import com.example.restriction.domain.ClientRestrictionRepository;
import com.example.restriction.domain.CustomerId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestrictionQueries {

    private final ClientRestrictionRepository restrictions;

    public RestrictionQueries(ClientRestrictionRepository restrictions) {
        this.restrictions = restrictions;
    }

    @Transactional(readOnly = true)
    public RestrictionView get(CustomerId customerId) {
        return RestrictionView.of(restrictions.findById(customerId));
    }
}
