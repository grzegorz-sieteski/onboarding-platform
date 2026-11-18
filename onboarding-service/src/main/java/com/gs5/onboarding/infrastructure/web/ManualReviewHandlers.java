package com.gs5.onboarding.infrastructure.web;

import com.gs5.onboarding.application.ApproveManualReviewCommand;
import com.gs5.onboarding.application.OnboardingCommandHandler;
import com.gs5.onboarding.application.OnboardingQueries;
import com.gs5.onboarding.application.RejectManualReviewCommand;
import com.gs5.onboarding.domain.CustomerId;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static com.gs5.onboarding.infrastructure.web.HttpResults.read;
import static com.gs5.onboarding.infrastructure.web.HttpResults.respond;

/** Compliance officers' API for onboardings whose screening timed out. */
final class ManualReviewHandlers {

    private final OnboardingCommandHandler commands;
    private final OnboardingQueries queries;

    ManualReviewHandlers(OnboardingCommandHandler commands, OnboardingQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    ServerResponse pending(ServerRequest request) {
        return ServerResponse.ok().body(queries.pendingManualReviews());
    }

    ServerResponse approve(ServerRequest request) {
        return respond(CustomerId.of(request.pathVariable("customerId"))
                        .flatMap(id -> read(request, ApproveRequest.class)
                                .flatMap(body -> commands.handle(
                                        new ApproveManualReviewCommand(id, body.reviewer(), body.comment()))))
                        .flatMap(queries::get),
                view -> ServerResponse.ok().body(view));
    }

    ServerResponse reject(ServerRequest request) {
        return respond(CustomerId.of(request.pathVariable("customerId"))
                        .flatMap(id -> read(request, RejectRequest.class)
                                .flatMap(body -> commands.handle(
                                        new RejectManualReviewCommand(id, body.reviewer(), body.reason()))))
                        .flatMap(queries::get),
                view -> ServerResponse.ok().body(view));
    }

    record ApproveRequest(String reviewer, String comment) {
    }

    record RejectRequest(String reviewer, String reason) {
    }
}
