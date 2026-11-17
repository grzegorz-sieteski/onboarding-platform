package com.example.screening.infrastructure.web;

import com.example.screening.application.ScreeningQueries;
import com.example.screening.application.ScreeningView;
import com.example.screening.domain.CustomerId;
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
