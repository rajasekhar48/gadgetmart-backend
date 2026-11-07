-- One row per (event, channel): the unique constraint makes duplicate Kafka deliveries harmless.
CREATE TABLE notifications (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL,
    type            VARCHAR(40)  NOT NULL,             -- WELCOME | ORDER_PAID | ORDER_CANCELLED
    channel         VARCHAR(10)  NOT NULL,             -- EMAIL | SMS | IN_APP
    recipient       VARCHAR(255),                      -- email address / phone; null for IN_APP
    subject         VARCHAR(255) NOT NULL,
    body            TEXT         NOT NULL,
    status          VARCHAR(10)  NOT NULL,             -- PENDING | SENT | FAILED
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(500),
    next_attempt_at TIMESTAMPTZ,
    source_event_id UUID         NOT NULL,
    ref_type        VARCHAR(20),                       -- e.g. ORDER (lets the UI deep-link)
    ref_id          UUID,
    created_at      TIMESTAMPTZ  NOT NULL,
    sent_at         TIMESTAMPTZ,
    read_at         TIMESTAMPTZ,
    CONSTRAINT uq_notification_event_channel UNIQUE (source_event_id, channel)
);

CREATE INDEX idx_notifications_user ON notifications (user_id, channel, created_at DESC);
CREATE INDEX idx_notifications_due  ON notifications (next_attempt_at) WHERE status = 'PENDING';

-- Message templates live in the database, so wording can change without a deployment.
CREATE TABLE templates (
    id               BIGSERIAL    PRIMARY KEY,
    code             VARCHAR(40)  NOT NULL,
    channel          VARCHAR(10)  NOT NULL,
    subject_template VARCHAR(255) NOT NULL,
    body_template    TEXT         NOT NULL,
    CONSTRAINT uq_template UNIQUE (code, channel)
);

-- Every delivery attempt, for support ("why did this email not arrive?").
CREATE TABLE delivery_log (
    id              UUID        PRIMARY KEY,
    notification_id UUID        NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    attempt_no      INT         NOT NULL,
    outcome         VARCHAR(10) NOT NULL,              -- SENT | RETRY | FAILED
    detail          VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_delivery_log_notification ON delivery_log (notification_id, attempt_no);

-- Idempotent consumer: an event id is claimed here in the same transaction that creates its notifications.
CREATE TABLE processed_events (
    event_id     UUID        PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
