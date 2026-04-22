CREATE TABLE IF NOT EXISTS event_store (
    id            BIGSERIAL PRIMARY KEY,
    aggregate_id  UUID        NOT NULL,
    version       BIGINT      NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       JSONB       NOT NULL,
    occurred_at   TIMESTAMP   NOT NULL,
    UNIQUE (aggregate_id, version)
);

CREATE INDEX IF NOT EXISTS idx_event_store_aggregate ON event_store (aggregate_id, version);

CREATE TABLE IF NOT EXISTS account_summary (
    id        UUID PRIMARY KEY,
    owner_id  VARCHAR(255) NOT NULL,
    currency  VARCHAR(10)  NOT NULL,
    balance   NUMERIC(19,4) NOT NULL DEFAULT 0
);
