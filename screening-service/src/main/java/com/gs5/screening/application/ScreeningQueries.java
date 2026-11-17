package com.gs5.screening.application;

import com.gs5.screening.domain.CustomerId;
import com.gs5.screening.domain.ScreeningCaseRepository;
import com.gs5.screening.domain.ScreeningError;
import com.gs5.screening.domain.shared.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScreeningQueries {

    private final ScreeningCaseRepository screenings;

    public ScreeningQueries(ScreeningCaseRepository screenings) {
        this.screenings = screenings;
    }

    @Transactional(readOnly = true)
    public Result<ScreeningView, ScreeningError> get(CustomerId customerId) {
        return screenings.load(customerId).map(ScreeningView::of);
    }
}
