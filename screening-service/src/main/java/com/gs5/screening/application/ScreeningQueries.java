package com.gs5.screening.application;

import com.gs5.screening.domain.CustomerId;
import com.gs5.screening.domain.ScreeningCaseRepository;
import com.gs5.screening.domain.ScreeningError;
import com.gs5.screening.domain.shared.Result;

public class ScreeningQueries {

    private final ScreeningCaseRepository screenings;
    private final UnitOfWork unitOfWork;

    public ScreeningQueries(ScreeningCaseRepository screenings, UnitOfWork unitOfWork) {
        this.screenings = screenings;
        this.unitOfWork = unitOfWork;
    }

    public Result<ScreeningView, ScreeningError> get(CustomerId customerId) {
        return unitOfWork.readOnly(() -> screenings.load(customerId).map(ScreeningView::of));
    }
}
