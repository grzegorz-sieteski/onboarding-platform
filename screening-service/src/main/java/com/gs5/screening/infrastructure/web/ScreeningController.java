package com.gs5.screening.infrastructure.web;

import com.gs5.screening.application.ScreeningQueries;
import com.gs5.screening.domain.CustomerId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.gs5.screening.infrastructure.web.ScreeningProblems.respond;

@RestController
@RequestMapping("/api/screenings")
class ScreeningController {

    private final ScreeningQueries queries;

    ScreeningController(ScreeningQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/{customerId}")
    ResponseEntity<?> get(@PathVariable String customerId) {
        return respond(CustomerId.of(customerId).flatMap(queries::get), view -> ResponseEntity.ok(view));
    }
}
