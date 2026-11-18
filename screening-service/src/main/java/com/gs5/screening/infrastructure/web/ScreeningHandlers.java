package com.gs5.screening.infrastructure.web;

import com.gs5.screening.application.ScreeningQueries;
import com.gs5.screening.domain.CustomerId;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static com.gs5.screening.infrastructure.web.HttpResults.respond;

/** HTTP adapter of the screening read side. */
final class ScreeningHandlers {

    private final ScreeningQueries queries;

    ScreeningHandlers(ScreeningQueries queries) {
        this.queries = queries;
    }

    ServerResponse get(ServerRequest request) {
        return respond(CustomerId.of(request.pathVariable("customerId")).flatMap(queries::get),
                view -> ServerResponse.ok().body(view));
    }
}
