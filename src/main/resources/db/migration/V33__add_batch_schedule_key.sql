ALTER TABLE batch_jobs
    ADD COLUMN schedule_key VARCHAR(120) NULL;

CREATE UNIQUE INDEX uq_batch_jobs_schedule_key
    ON batch_jobs (schedule_key);
