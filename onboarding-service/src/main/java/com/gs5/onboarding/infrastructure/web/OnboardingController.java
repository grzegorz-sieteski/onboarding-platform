package com.gs5.onboarding.infrastructure.web;

import com.gs5.onboarding.application.OnboardingCommandHandler;
import com.gs5.onboarding.application.OnboardingQueries;
import com.gs5.onboarding.domain.CustomerId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

import static com.gs5.onboarding.infrastructure.web.OnboardingProblems.respond;

@RestController
@RequestMapping("/api/onboardings")
class OnboardingController {

    private final OnboardingCommandHandler commands;
    private final OnboardingQueries queries;

    OnboardingController(OnboardingCommandHandler commands, OnboardingQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    ResponseEntity<?> start(@Valid @RequestBody StartOnboardingRequest request) {
        return respond(commands.handle(request.toCommand()).flatMap(queries::get),
                view -> ResponseEntity.created(URI.create("/api/onboardings/" + view.customerId())).body(view));
    }

    @GetMapping("/{customerId}")
    ResponseEntity<?> get(@PathVariable String customerId) {
        return respond(CustomerId.of(customerId).flatMap(queries::get), view -> ResponseEntity.ok(view));
    }
}
