-- V21: User feedback with AI analysis support
CREATE TABLE feedback (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          REFERENCES users(id),
    content         TEXT            NOT NULL,
    contact         VARCHAR(255),
    category        VARCHAR(50),    -- AI: BUG / FEATURE_REQUEST / UX / PERFORMANCE / CONTENT_QUALITY / OTHER
    severity        VARCHAR(20),    -- AI: LOW / MEDIUM / HIGH / CRITICAL
    escalate_to_tech BOOLEAN       DEFAULT FALSE,
    ai_analysis     TEXT,           -- Full AI analysis JSON
    status          VARCHAR(20)     DEFAULT 'PENDING',  -- PENDING / REVIEWED / ESCALATED / RESOLVED / DISMISSED
    extra_attributes JSONB,
    created_at      TIMESTAMPTZ     DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     DEFAULT NOW()
);

CREATE INDEX idx_feedback_category ON feedback(category);
CREATE INDEX idx_feedback_severity ON feedback(severity);
CREATE INDEX idx_feedback_status   ON feedback(status);
