package com.gs5.screening.infrastructure.eventstore;

/** An append lost the race: another writer already stored the version this one wanted to write. */
public record StreamConflict(String streamId, long expectedVersion) {
}
