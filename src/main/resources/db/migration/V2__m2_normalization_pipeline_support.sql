ALTER TABLE book
    ADD COLUMN analysis_status VARCHAR(30) NULL AFTER normalization_status;

UPDATE book
SET analysis_status = CASE
    WHEN summary = 1 THEN 'READY'
    ELSE 'NONE'
END
WHERE analysis_status IS NULL;

CREATE TABLE processing_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    book_id BIGINT NOT NULL,
    pipeline_type VARCHAR(30) NOT NULL,
    run_id VARCHAR(80) NOT NULL,
    source_version VARCHAR(80),
    artifact_path VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    current_step VARCHAR(80),
    failure_code VARCHAR(80),
    failure_message TEXT,
    triggered_by VARCHAR(80),
    started_at DATETIME(6),
    finished_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_processing_job_book
        FOREIGN KEY (book_id) REFERENCES book (id)
);

CREATE INDEX idx_processing_job_book_pipeline
    ON processing_job (book_id, pipeline_type);

CREATE INDEX idx_processing_job_status
    ON processing_job (status);

CREATE TABLE processing_job_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    job_id BIGINT NOT NULL,
    seq INTEGER NOT NULL,
    level VARCHAR(20) NOT NULL,
    step VARCHAR(80),
    message TEXT NOT NULL,
    payload_json TEXT,
    PRIMARY KEY (id),
    CONSTRAINT fk_processing_job_log_job
        FOREIGN KEY (job_id) REFERENCES processing_job (id)
);

CREATE INDEX idx_processing_job_log_job_seq
    ON processing_job_log (job_id, seq);
