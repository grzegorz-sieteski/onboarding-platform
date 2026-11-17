package com.gs5.restriction.infrastructure.outbox;

record OutboxEntry(long position, OutboxMessage message) {
}
