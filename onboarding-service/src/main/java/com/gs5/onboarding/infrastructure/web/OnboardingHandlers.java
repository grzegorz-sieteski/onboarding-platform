package com.gs5.onboarding.infrastructure.web;

import com.gs5.onboarding.application.OnboardingCommandHandler;
import com.gs5.onboarding.application.OnboardingQueries;
import com.gs5.onboarding.domain.CustomerId;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

import static com.gs5.onboarding.infrastructure.web.HttpResults.read;
import static com.gs5.onboarding.infrastructure.web.HttpResults.respond;

/** HTTP adapter of the onboarding use cases: a request becomes a command, a result becomes a response. */
final class OnboardingHandlers {

    private final OnboardingCommandHandler commands;
    private final OnboardingQueries queries;

    OnboardingHandlers(OnboardingCommandHandler commands, OnboardingQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    ServerResponse start(ServerRequest request) {
        return respond(read(request, StartOnboardingRequest.class)
                        .flatMap(body -> commands.handle(body.toCommand()))
                        .flatMap(queries::get),
                view -> ServerResponse.created(URI.create("/api/onboardings/" + view.customerId())).body(view));
    }

    ServerResponse get(ServerRequest request) {
        return respond(CustomerId.of(request.pathVariable("customerId")).flatMap(queries::get),
                view -> ServerResponse.ok().body(view));
    }
}
