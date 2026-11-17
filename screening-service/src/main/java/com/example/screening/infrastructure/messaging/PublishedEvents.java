package com.example.screening.infrastructure.messaging;

/** Published language of the Screening context. */
final class PublishedEvents {

    static final String CLIENT_RESTRICTED = "ClientRestricted";
    static final String CLIENT_CLEARED = "ClientCleared";

    private PublishedEvents() {
    }

    record ClientRestrictedV1(String customerId, String matchedSurname, String reason) {
    }

    record ClientClearedV1(String customerId) {
    }
}
