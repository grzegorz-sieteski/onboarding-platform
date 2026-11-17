-- Append-only event store. (stream_id, stream_version) = optimistic concurrency + per-aggregate ordering.
CREATE TABLE event_store (
    global_position BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stream_id       VARCHAR(120)             NOT NULL,
    stream_version  BIGINT                   NOT NULL,
    event_type      VARCHAR(120)             NOT NULL,
    payload         TEXT                     NOT NULL,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_event_store_stream_version UNIQUE (stream_id, stream_version)
);

-- Transactional outbox, written in the same transaction as event_store.
CREATE TABLE outbox (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id   UUID                     NOT NULL UNIQUE,
    topic        VARCHAR(200)             NOT NULL,
    message_key  VARCHAR(200)             NOT NULL,
    event_type   VARCHAR(120)             NOT NULL,
    payload      TEXT                     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_outbox_unpublished ON outbox (published_at, id);

-- Inbox: ids of consumed messages, for idempotent handling of at-least-once delivery.
CREATE TABLE processed_message (
    message_id   UUID PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ShedLock: single active outbox relay across replicas.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
