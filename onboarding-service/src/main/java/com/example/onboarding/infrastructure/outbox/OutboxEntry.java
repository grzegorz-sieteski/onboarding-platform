package com.example.onboarding.infrastructure.outbox;

record OutboxEntry(long position, OutboxMessage message) {
}
