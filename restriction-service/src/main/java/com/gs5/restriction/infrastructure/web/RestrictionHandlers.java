package com.gs5.restriction.infrastructure.web;

import com.gs5.restriction.application.BanClientCommand;
import com.gs5.restriction.application.RestrictionCommandHandler;
import com.gs5.restriction.application.RestrictionQueries;
import com.gs5.restriction.domain.CustomerId;
import com.gs5.restriction.domain.RestrictionSource;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static com.gs5.restriction.infrastructure.web.HttpResults.read;
import static com.gs5.restriction.infrastructure.web.HttpResults.respond;

/** HTTP adapter of the restriction use cases. */
final class RestrictionHandlers {

    private final RestrictionCommandHandler commands;
    private final RestrictionQueries queries;

    RestrictionHandlers(RestrictionCommandHandler commands, RestrictionQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    ServerResponse get(ServerRequest request) {
        return respond(CustomerId.of(request.pathVariable("customerId")).flatMap(queries::get),
                view -> ServerResponse.ok().body(view));
    }

    /** Manual ban by a compliance officer - goes through the same aggregate and the same events. */
    ServerResponse ban(ServerRequest request) {
        return respond(CustomerId.of(request.pathVariable("customerId"))
                        .flatMap(id -> read(request, BanRequest.class)
                                .flatMap(body -> commands.handle(
                                        new BanClientCommand(id, body.reason(), RestrictionSource.MANUAL))))
                        .flatMap(queries::get),
                view -> ServerResponse.ok().body(view));
    }

    /** Request body; deliberately not validated here - the domain validates it. */
    record BanRequest(String reason) {
    }
}
