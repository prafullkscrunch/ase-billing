-- audit_logs already exists from V1. This only pins the default so a row written
-- by JPA and one written by hand look the same, and adds the index the
-- "what happened to this invoice" lookup uses.
ALTER TABLE audit_logs
    MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX ix_audit_created ON audit_logs (created_at);
