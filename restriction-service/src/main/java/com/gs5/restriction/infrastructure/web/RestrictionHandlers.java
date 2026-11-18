package com.gs5.restriction.infrastructure.web;

import com.gs5.restriction.application.BanClientCommand;
import com.gs5.restriction.application.RestrictionCommandHandler;
import com.gs5.restriction.application.RestrictionQueries;
import com.gs5.restriction.domain.CustomerId;
import com.gs5.restriction.domain.RestrictionError;
import com.gs5.restriction.domain.RestrictionError.InvalidInput;
import com.gs5.restriction.domain.RestrictionSource;
import com.gs5.restriction.domain.shared.Result;
import com.gs5.restriction.domain.shared.Validation;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.function.BiFunction;

import static com.gs5.restriction.infrastructure.web.HttpResults.customerId;
import static com.gs5.restriction.infrastructure.web.HttpResults.read;

/** HTTP adapter of the restriction use cases. Every endpoint reads top-down: request, command, result, response. */
final class RestrictionHandlers {

    private final RestrictionCommandHandler commands;
    private final RestrictionQueries queries;

    RestrictionHandlers(RestrictionCommandHandler commands, RestrictionQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    ServerResponse get(ServerRequest request) {
        return customerId(request)
                .flatMap(queries::get)
                .fold(HttpResults::ok, HttpResults::problem);
    }

    /** Manual ban by a compliance officer - goes through the same aggregate and the same events. */
    ServerResponse ban(ServerRequest request) {
        return command(request, BanRequest.class, BanRequest::toCommand)
                .flatMap(commands::handle)
                .flatMap(queries::get)
                .fold(HttpResults::ok, HttpResults::problem);
    }

    /** The customer id from the path and the body are read together, so every problem with the request is reported. */
    private static <B, C> Result<C, RestrictionError> command(ServerRequest request, Class<B> bodyType,
                                                           BiFunction<B, CustomerId, C> toCommand) {
        var input = Validation.of(InvalidInput::new);
        var id = input.check(customerId(request));
        var body = input.check(read(request, bodyType));
        return input.build(() -> toCommand.apply(body.value(), id.value()));
    }

    /** Request body; deliberately not validated here - the domain validates it. */
    record BanRequest(String reason) {

        BanClientCommand toCommand(CustomerId customerId) {
            return new BanClientCommand(customerId, reason, RestrictionSource.MANUAL);
        }
    }
}
