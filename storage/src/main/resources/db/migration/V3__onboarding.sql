CREATE TABLE IF NOT EXISTS onboarding_jobs (
                                               job_id       VARCHAR(64) PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    phase        VARCHAR(32) NOT NULL,
    progress     INT NOT NULL DEFAULT 0,
    per_bank     JSONB NOT NULL DEFAULT '{}'::jsonb,
    obligations_detected INT,
    error        TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_onb_user ON onboarding_jobs(user_id);

-- re-use updated_at trigger from V1
DROP TRIGGER IF EXISTS trg_onb_updated_at ON onboarding_jobs;
CREATE TRIGGER trg_onb_updated_at
    BEFORE UPDATE ON onboarding_jobs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
