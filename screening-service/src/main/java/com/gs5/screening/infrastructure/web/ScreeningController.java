package com.gs5.screening.infrastructure.web;

import com.gs5.screening.application.ScreeningQueries;
import com.gs5.screening.application.ScreeningView;
import com.gs5.screening.domain.CustomerId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/screenings")
class ScreeningController {

    private final ScreeningQueries queries;

    ScreeningController(ScreeningQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/{customerId}")
    ScreeningView get(@PathVariable String customerId) {
        return queries.get(CustomerId.of(customerId));
    }
}
