package com.gs5.onboarding.infrastructure.config;

import com.gs5.onboarding.application.ManualReviewQueue;
import com.gs5.onboarding.application.OnboardingCommandHandler;
import com.gs5.onboarding.application.OnboardingQueries;
import com.gs5.onboarding.application.OverdueScreenings;
import com.gs5.onboarding.application.ScreeningTimeouts;
import com.gs5.onboarding.application.UnitOfWork;
import com.gs5.onboarding.domain.CustomerOnboardingRepository;
import com.gs5.onboarding.domain.EligibilityPolicy;
import com.gs5.onboarding.domain.ScreeningSla;
import com.gs5.onboarding.infrastructure.scheduling.ScreeningProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wiring of the framework-free core: domain policies and the application's use cases. */
@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    EligibilityPolicy eligibilityPolicy() {
        return new EligibilityPolicy();
    }

    @Bean
    ScreeningSla screeningSla(ScreeningProperties properties) {
        // a bad timeout in the configuration is a startup failure, not a business outcome
        return ScreeningSla.of(properties.timeout())
                .orElseThrow(error -> new IllegalStateException("app.screening.timeout: " + error.message()));
    }

    @Bean
    OnboardingCommandHandler onboardingCommandHandler(CustomerOnboardingRepository onboardings,
                                                      UnitOfWork unitOfWork, EligibilityPolicy eligibilityPolicy,
                                                      ScreeningSla screeningSla, Clock clock) {
        return new OnboardingCommandHandler(onboardings, unitOfWork, eligibilityPolicy, screeningSla, clock);
    }

    @Bean
    OnboardingQueries onboardingQueries(CustomerOnboardingRepository onboardings, ManualReviewQueue manualReviewQueue,
                                        UnitOfWork unitOfWork) {
        return new OnboardingQueries(onboardings, manualReviewQueue, unitOfWork);
    }

    @Bean
    ScreeningTimeouts screeningTimeouts(OverdueScreenings overdueScreenings, OnboardingCommandHandler commands,
                                        Clock clock) {
        return new ScreeningTimeouts(overdueScreenings, commands, clock);
    }
}
