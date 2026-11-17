package com.example.screening.infrastructure.outbox;

record OutboxEntry(long position, OutboxMessage message) {
}
