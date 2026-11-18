package com.gs5.onboarding.infrastructure.web;

import com.gs5.onboarding.application.OnboardingCommandHandler;
import com.gs5.onboarding.application.OnboardingQueries;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/** The whole HTTP API of the service in one place: every route and the handler that serves it. */
@Configuration(proxyBeanMethods = false)
public class WebConfiguration {

    @Bean
    RouterFunction<ServerResponse> routes(OnboardingCommandHandler commands, OnboardingQueries queries) {
        var onboardings = new OnboardingHandlers(commands, queries);
        var manualReviews = new ManualReviewHandlers(commands, queries);
        return RouterFunctions.route()
                .POST("/api/onboardings", onboardings::start)
                .GET("/api/onboardings/{customerId}", onboardings::get)
                .GET("/api/manual-reviews", manualReviews::pending)
                .POST("/api/manual-reviews/{customerId}/approve", manualReviews::approve)
                .POST("/api/manual-reviews/{customerId}/reject", manualReviews::reject)
                .build();
    }
}
