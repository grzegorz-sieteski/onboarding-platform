package com.example.screening.application;

import com.example.screening.domain.CustomerId;
import com.example.screening.domain.ScreeningCaseRepository;
import com.example.screening.domain.ScreeningNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScreeningQueries {

    private final ScreeningCaseRepository screenings;

    public ScreeningQueries(ScreeningCaseRepository screenings) {
        this.screenings = screenings;
    }

    @Transactional(readOnly = true)
    public ScreeningView get(CustomerId customerId) {
        return screenings.findById(customerId)
                .map(ScreeningView::of)
                .orElseThrow(() -> new ScreeningNotFoundException(customerId));
    }
}
