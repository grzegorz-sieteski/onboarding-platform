package com.example.onboarding.infrastructure.web;

import com.example.onboarding.application.OnboardingCommandHandler;
import com.example.onboarding.application.OnboardingQueries;
import com.example.onboarding.application.OnboardingView;
import com.example.onboarding.domain.CustomerId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

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
    ResponseEntity<OnboardingView> start(@Valid @RequestBody StartOnboardingRequest request) {
        CustomerId customerId = commands.handle(request.toCommand());
        return ResponseEntity.created(URI.create("/api/onboardings/" + customerId))
                .body(queries.get(customerId));
    }

    @GetMapping("/{customerId}")
    OnboardingView get(@PathVariable String customerId) {
        return queries.get(CustomerId.of(customerId));
    }
}
