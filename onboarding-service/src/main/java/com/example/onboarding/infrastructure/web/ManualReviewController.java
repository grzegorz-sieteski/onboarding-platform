package com.example.onboarding.infrastructure.web;

import com.example.onboarding.application.ApproveManualReviewCommand;
import com.example.onboarding.application.ManualReviewItem;
import com.example.onboarding.application.OnboardingCommandHandler;
import com.example.onboarding.application.OnboardingQueries;
import com.example.onboarding.application.OnboardingView;
import com.example.onboarding.application.RejectManualReviewCommand;
import com.example.onboarding.domain.CustomerId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Compliance officers' API for onboardings whose screening timed out. */
@RestController
@RequestMapping("/api/manual-reviews")
class ManualReviewController {

    private final OnboardingCommandHandler commands;
    private final OnboardingQueries queries;

    ManualReviewController(OnboardingCommandHandler commands, OnboardingQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping
    List<ManualReviewItem> pending() {
        return queries.pendingManualReviews();
    }

    @PostMapping("/{customerId}/approve")
    OnboardingView approve(@PathVariable String customerId, @Valid @RequestBody ApproveRequest request) {
        CustomerId id = CustomerId.of(customerId);
        commands.handle(new ApproveManualReviewCommand(id, request.reviewer(), request.comment()));
        return queries.get(id);
    }

    @PostMapping("/{customerId}/reject")
    OnboardingView reject(@PathVariable String customerId, @Valid @RequestBody RejectRequest request) {
        CustomerId id = CustomerId.of(customerId);
        commands.handle(new RejectManualReviewCommand(id, request.reviewer(), request.reason()));
        return queries.get(id);
    }

    record ApproveRequest(@NotBlank String reviewer, String comment) {
    }

    record RejectRequest(@NotBlank String reviewer, @NotBlank String reason) {
    }
}
