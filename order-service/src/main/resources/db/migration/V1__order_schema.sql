CREATE TABLE orders (
    id              UUID         PRIMARY KEY,
    order_number    VARCHAR(24)  NOT NULL UNIQUE,            -- human friendly, e.g. GM-261026-7K2QX9AB
    user_id         UUID         NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    idempotency_key VARCHAR(64)  NOT NULL,
    total_minor     BIGINT       NOT NULL CHECK (total_minor > 0),   -- paise
    currency        VARCHAR(3)   NOT NULL,
    -- shipping address SNAPSHOT: later profile edits never change a placed order
    ship_full_name  VARCHAR(120) NOT NULL,
    ship_phone      VARCHAR(20)  NOT NULL,
    ship_line1      VARCHAR(200) NOT NULL,
    ship_line2      VARCHAR(200),
    ship_city       VARCHAR(80)  NOT NULL,
    ship_state      VARCHAR(80)  NOT NULL,
    ship_pincode    VARCHAR(10)  NOT NULL,
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_order_idempotency UNIQUE (user_id, idempotency_key)   -- same key twice = same order
);

CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_pending      ON orders (created_at) WHERE status = 'PENDING_PAYMENT';

-- Title / price are SNAPSHOTS taken at order time.
CREATE TABLE order_items (
    id               UUID         PRIMARY KEY,
    order_id         UUID         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    listing_id       UUID         NOT NULL,
    title            VARCHAR(200) NOT NULL,
    seller_name      VARCHAR(120),
    thumbnail_url    VARCHAR(500),
    unit_price_minor BIGINT       NOT NULL CHECK (unit_price_minor > 0),
    quantity         INT          NOT NULL CHECK (quantity > 0),
    line_total_minor BIGINT       NOT NULL CHECK (line_total_minor > 0)
);

CREATE INDEX idx_order_items_order ON order_items (order_id);

CREATE TABLE order_status_history (
    id          UUID        PRIMARY KEY,
    order_id    UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    reason      VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_status_history_order ON order_status_history (order_id, created_at);

CREATE TABLE payments (
    id                 UUID         PRIMARY KEY,
    order_id           UUID         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    provider           VARCHAR(30)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,        -- PENDING | SUCCEEDED | FAILED | REFUNDED
    amount_minor       BIGINT       NOT NULL,
    currency           VARCHAR(3)   NOT NULL,
    provider_reference VARCHAR(100),
    failure_reason     VARCHAR(255),
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_payments_order ON payments (order_id);

-- Transactional outbox: events are written in the SAME transaction as the state change.
-- A relay (added with Kafka / Notification Service) will publish rows where published_at IS NULL.
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
