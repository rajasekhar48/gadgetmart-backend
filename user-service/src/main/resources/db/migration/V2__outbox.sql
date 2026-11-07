-- Transactional outbox: written in the same transaction as the business change, published to Kafka by a relay.
CREATE TABLE outbox_events (
    id             UUID        PRIMARY KEY,
    aggregate_type VARCHAR(30) NOT NULL,
    aggregate_id   UUID        NOT NULL,
    event_type     VARCHAR(50) NOT NULL,
    payload        JSONB       NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
