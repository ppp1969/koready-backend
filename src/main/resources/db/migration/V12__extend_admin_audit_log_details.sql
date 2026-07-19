ALTER TABLE admin_audit_logs
    ADD COLUMN reason VARCHAR(500) NULL AFTER resource_id,
    ADD COLUMN before_snapshot JSON NULL AFTER after_status,
    ADD COLUMN after_snapshot JSON NULL AFTER before_snapshot;
