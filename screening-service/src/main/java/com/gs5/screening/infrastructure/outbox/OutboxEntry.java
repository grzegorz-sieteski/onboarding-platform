package com.gs5.screening.infrastructure.outbox;

record OutboxEntry(long position, OutboxMessage message) {
}
