ALTER TABLE batch_jobs
    ADD COLUMN active_execution_slot TINYINT NULL;

CREATE UNIQUE INDEX uq_batch_jobs_active_execution_slot
    ON batch_jobs (active_execution_slot);
