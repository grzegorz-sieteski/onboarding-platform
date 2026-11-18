package com.gs5.onboarding.infrastructure.web;

import com.gs5.onboarding.application.OnboardingCommandHandler;
import com.gs5.onboarding.application.OnboardingQueries;
import com.gs5.onboarding.application.OnboardingView;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

import static com.gs5.onboarding.infrastructure.web.HttpResults.customerId;
import static com.gs5.onboarding.infrastructure.web.HttpResults.read;

/** HTTP adapter of the onboarding use cases. Every endpoint reads top-down: request, command, result, response. */
final class OnboardingHandlers {

    private final OnboardingCommandHandler commands;
    private final OnboardingQueries queries;

    OnboardingHandlers(OnboardingCommandHandler commands, OnboardingQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    ServerResponse start(ServerRequest request) {
        return read(request, StartOnboardingRequest.class)
                .map(StartOnboardingRequest::toCommand)
                .flatMap(commands::handle)
                .flatMap(queries::get)
                .fold(OnboardingHandlers::created, HttpResults::problem);
    }

    ServerResponse get(ServerRequest request) {
        return customerId(request)
                .flatMap(queries::get)
                .fold(HttpResults::ok, HttpResults::problem);
    }

    private static ServerResponse created(OnboardingView view) {
        return ServerResponse.created(URI.create("/api/onboardings/" + view.customerId())).body(view);
    }
}
