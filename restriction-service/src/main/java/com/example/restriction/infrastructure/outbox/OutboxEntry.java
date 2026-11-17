package com.example.restriction.infrastructure.outbox;

record OutboxEntry(long position, OutboxMessage message) {
}
