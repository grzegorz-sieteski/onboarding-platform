package com.example.onboarding.infrastructure.messaging;

import com.example.onboarding.domain.CustomerOnboarding;
import com.example.onboarding.domain.OnboardingEvent;
import com.example.onboarding.domain.OnboardingEvent.ApplicantVerified;
import com.example.onboarding.domain.OnboardingEvent.CustomerOnboarded;
import com.example.onboarding.domain.OnboardingEvent.ManualReviewApproved;
import com.example.onboarding.domain.OnboardingEvent.ManualReviewRejected;
import com.example.onboarding.domain.OnboardingEvent.ManualReviewRequested;
import com.example.onboarding.domain.OnboardingEvent.OnboardingBanned;
import com.example.onboarding.domain.OnboardingEvent.OnboardingRejected;
import com.example.onboarding.domain.OnboardingEvent.OnboardingStarted;
import com.example.onboarding.infrastructure.messaging.PublishedEvents.ApplicantVerifiedV1;
import com.example.onboarding.infrastructure.messaging.PublishedEvents.CustomerOnboardedV1;
import com.example.onboarding.infrastructure.messaging.PublishedEvents.OnboardingBannedV1;
import com.example.onboarding.infrastructure.outbox.OutboxMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Translates internal domain events into the published language. Only facts other contexts care about leave. */
@Component
public class OnboardingIntegrationEvents {

    private final IntegrationEventCodec codec;
    private final String topic;

    OnboardingIntegrationEvents(IntegrationEventCodec codec, MessagingProperties properties) {
        this.codec = codec;
        this.topic = properties.topics().onboardingEvents();
    }

    /** Must be called before the events are marked as committed - versions are derived from the aggregate. */
    public List<OutboxMessage> toOutboxMessages(CustomerOnboarding onboarding, List<OnboardingEvent> newEvents) {
        var messages = new ArrayList<OutboxMessage>();
        long version = onboarding.version();
        for (OnboardingEvent event : newEvents) {
            long eventVersion = ++version;
            toContract(onboarding, event)
                    .map(contract -> codec.encode(topic, contract.type(), onboarding.customerId().toString(),
                            eventVersion, event.occurredAt(), contract.payload()))
                    .ifPresent(messages::add);
        }
        return messages;
    }

    private static Optional<Contract> toContract(CustomerOnboarding onboarding, OnboardingEvent event) {
        return switch (event) {
            case ApplicantVerified verified -> Optional.of(new Contract(PublishedEvents.APPLICANT_VERIFIED,
                    new ApplicantVerifiedV1(verified.customerId().toString(),
                            onboarding.applicant().name().firstName(),
                            onboarding.applicant().name().lastName(),
                            onboarding.applicant().dateOfBirth())));
            case CustomerOnboarded onboarded -> Optional.of(new Contract(PublishedEvents.CUSTOMER_ONBOARDED,
                    new CustomerOnboardedV1(onboarded.customerId().toString(),
                            onboarding.applicant().name().firstName(),
                            onboarding.applicant().name().lastName(),
                            onboarding.applicant().dateOfBirth())));
            case OnboardingBanned banned -> Optional.of(new Contract(PublishedEvents.ONBOARDING_BANNED,
                    new OnboardingBannedV1(banned.customerId().toString(), banned.reason())));
            case OnboardingStarted internalOnly -> Optional.empty();
            case OnboardingRejected internalOnly -> Optional.empty();
            case ManualReviewRequested internalOnly -> Optional.empty();
            case ManualReviewApproved internalOnly -> Optional.empty();
            case ManualReviewRejected internalOnly -> Optional.empty();
        };
    }

    private record Contract(String type, Object payload) {
    }
}
