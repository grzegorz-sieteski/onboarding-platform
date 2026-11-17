package com.gs5.restriction.infrastructure.messaging;

/** Published language of the Restriction context. */
final class PublishedEvents {

    static final String CLIENT_BANNED = "ClientBanned";

    private PublishedEvents() {
    }

    record ClientBannedV1(String customerId, String reason, String source) {
    }
}
