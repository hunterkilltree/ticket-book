CREATE TABLE admin_audit_log (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID        NOT NULL,
    actor_email VARCHAR(255) NOT NULL,
    action      VARCHAR(100) NOT NULL,
    target_type VARCHAR(100),
    target_id   UUID,
    detail      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_actor   ON admin_audit_log(actor_id);
CREATE INDEX idx_audit_log_created ON admin_audit_log(created_at DESC);
