package com.gs5.onboarding.infrastructure.web;

import com.gs5.onboarding.application.ApproveManualReviewCommand;
import com.gs5.onboarding.application.OnboardingCommandHandler;
import com.gs5.onboarding.application.OnboardingQueries;
import com.gs5.onboarding.application.RejectManualReviewCommand;
import com.gs5.onboarding.domain.CustomerId;
import com.gs5.onboarding.domain.OnboardingError;
import com.gs5.onboarding.domain.OnboardingError.InvalidInput;
import com.gs5.onboarding.domain.shared.Result;
import com.gs5.onboarding.domain.shared.Validation;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.function.BiFunction;

import static com.gs5.onboarding.infrastructure.web.HttpResults.customerId;
import static com.gs5.onboarding.infrastructure.web.HttpResults.read;

/** Compliance officers' API for onboardings whose screening timed out. */
final class ManualReviewHandlers {

    private final OnboardingCommandHandler commands;
    private final OnboardingQueries queries;

    ManualReviewHandlers(OnboardingCommandHandler commands, OnboardingQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    ServerResponse pending(ServerRequest request) {
        return HttpResults.ok(queries.pendingManualReviews());
    }

    ServerResponse approve(ServerRequest request) {
        return command(request, ApproveRequest.class, ApproveRequest::toCommand)
                .flatMap(commands::handle)
                .flatMap(queries::get)
                .fold(HttpResults::ok, HttpResults::problem);
    }

    ServerResponse reject(ServerRequest request) {
        return command(request, RejectRequest.class, RejectRequest::toCommand)
                .flatMap(commands::handle)
                .flatMap(queries::get)
                .fold(HttpResults::ok, HttpResults::problem);
    }

    /** The customer id from the path and the body are read together, so every problem with the request is reported. */
    private static <B, C> Result<C, OnboardingError> command(ServerRequest request, Class<B> bodyType,
                                                          BiFunction<B, CustomerId, C> toCommand) {
        var input = Validation.of(InvalidInput::new);
        var id = input.check(customerId(request));
        var body = input.check(read(request, bodyType));
        return input.build(() -> toCommand.apply(body.value(), id.value()));
    }

    record ApproveRequest(String reviewer, String comment) {

        ApproveManualReviewCommand toCommand(CustomerId customerId) {
            return new ApproveManualReviewCommand(customerId, reviewer, comment);
        }
    }

    record RejectRequest(String reviewer, String reason) {

        RejectManualReviewCommand toCommand(CustomerId customerId) {
            return new RejectManualReviewCommand(customerId, reviewer, reason);
        }
    }
}
