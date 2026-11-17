package com.gs5.onboarding.infrastructure.web;

import com.gs5.onboarding.application.ApproveManualReviewCommand;
import com.gs5.onboarding.application.ManualReviewItem;
import com.gs5.onboarding.application.OnboardingCommandHandler;
import com.gs5.onboarding.application.OnboardingQueries;
import com.gs5.onboarding.application.RejectManualReviewCommand;
import com.gs5.onboarding.domain.CustomerId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.gs5.onboarding.infrastructure.web.OnboardingProblems.respond;

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
    ResponseEntity<?> approve(@PathVariable String customerId, @Valid @RequestBody ApproveRequest request) {
        return respond(CustomerId.of(customerId)
                        .flatMap(id -> commands.handle(
                                new ApproveManualReviewCommand(id, request.reviewer(), request.comment())))
                        .flatMap(queries::get),
                view -> ResponseEntity.ok(view));
    }

    @PostMapping("/{customerId}/reject")
    ResponseEntity<?> reject(@PathVariable String customerId, @Valid @RequestBody RejectRequest request) {
        return respond(CustomerId.of(customerId)
                        .flatMap(id -> commands.handle(
                                new RejectManualReviewCommand(id, request.reviewer(), request.reason())))
                        .flatMap(queries::get),
                view -> ResponseEntity.ok(view));
    }

    record ApproveRequest(@NotBlank String reviewer, String comment) {
    }

    record RejectRequest(@NotBlank String reviewer, @NotBlank String reason) {
    }
}
