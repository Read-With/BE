ALTER TABLE book
    ADD COLUMN normalization_run_id VARCHAR(80) NULL AFTER normalized_artifact_path;

ALTER TABLE processing_job
    ADD COLUMN rule_version VARCHAR(50) NULL AFTER artifact_path,
    ADD COLUMN locator_version VARCHAR(50) NULL AFTER rule_version;
