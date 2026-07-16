ALTER TABLE processing_job
    ADD COLUMN external_job_id VARCHAR(120) NULL AFTER artifact_path,
    ADD COLUMN input_file_id VARCHAR(120) NULL AFTER external_job_id,
    ADD COLUMN output_file_id VARCHAR(120) NULL AFTER input_file_id,
    ADD COLUMN error_file_id VARCHAR(120) NULL AFTER output_file_id;

CREATE INDEX idx_processing_job_external_job
    ON processing_job (external_job_id);

ALTER TABLE character_image_asset
    ADD COLUMN processing_job_id BIGINT NULL AFTER source_reference_asset_id,
    ADD CONSTRAINT fk_character_image_asset_processing_job
        FOREIGN KEY (processing_job_id) REFERENCES processing_job (id);

CREATE INDEX idx_character_image_asset_processing_job
    ON character_image_asset (processing_job_id);
