-- Inline projections of the CustomerOnboarding stream (rebuildable from event_store at any time).

-- Open screening windows; the timeout job escalates rows whose due_at has passed.
CREATE TABLE screening_deadline (
    customer_id UUID PRIMARY KEY,
    due_at      TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_screening_deadline_due_at ON screening_deadline (due_at);

-- Work queue of compliance officers.
CREATE TABLE manual_review_queue (
    customer_id  UUID PRIMARY KEY,
    first_name   VARCHAR(100)             NOT NULL,
    last_name    VARCHAR(100)             NOT NULL,
    reason       VARCHAR(500)             NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL
);
