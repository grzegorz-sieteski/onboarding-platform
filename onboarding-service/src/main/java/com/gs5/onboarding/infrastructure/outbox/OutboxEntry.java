package com.gs5.onboarding.infrastructure.outbox;

record OutboxEntry(long position, OutboxMessage message) {
}
