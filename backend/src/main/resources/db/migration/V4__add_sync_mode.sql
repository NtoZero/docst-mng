-- Add sync mode columns to dm_sync_job table

-- Add sync_mode column with default FULL_SCAN
ALTER TABLE dm_sync_job
ADD COLUMN sync_mode VARCHAR(20) DEFAULT 'FULL_SCAN' NOT NULL;

-- Add target_commit_sha column for SPECIFIC_COMMIT mode
ALTER TABLE dm_sync_job
ADD COLUMN target_commit_sha VARCHAR(64);

-- Add comments
COMMENT ON COLUMN dm_sync_job.sync_mode IS 'Sync mode: FULL_SCAN, INCREMENTAL, SPECIFIC_COMMIT';
COMMENT ON COLUMN dm_sync_job.target_commit_sha IS 'Target commit SHA for SPECIFIC_COMMIT mode';

-- Add index on sync_mode for filtering
CREATE INDEX idx_sync_job_mode ON dm_sync_job(sync_mode);
