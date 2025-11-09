CREATE TABLE IF NOT EXISTS payment_runs (
                                            id            BIGSERIAL PRIMARY KEY,
                                            obligation_id BIGINT      NOT NULL,
                                            scheduled_at  TIMESTAMP   NOT NULL,
                                            amount_cents  BIGINT      NOT NULL,
                                            status        VARCHAR(16) NOT NULL,  -- PENDING|DUE|PROCESSING|DONE|FAILED
    payment_id    VARCHAR(128),
    fail_reason   TEXT,
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP   NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_runs_due ON payment_runs(status, scheduled_at);

