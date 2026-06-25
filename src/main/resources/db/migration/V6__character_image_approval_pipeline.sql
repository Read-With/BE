CREATE TABLE character_image_asset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    book_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    asset_role VARCHAR(30) NOT NULL,
    generation_mode VARCHAR(30) NOT NULL,
    source_reference_asset_id BIGINT,
    reference_version INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    s3_url VARCHAR(1024),
    model VARCHAR(80),
    prompt_hash VARCHAR(64),
    qa_result_json TEXT,
    failure_code VARCHAR(80),
    openai_request_id VARCHAR(120),
    attempt_no INTEGER NOT NULL DEFAULT 1,
    published_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_character_image_asset_book
        FOREIGN KEY (book_id) REFERENCES book (id),
    CONSTRAINT fk_character_image_asset_character
        FOREIGN KEY (character_id) REFERENCES book_character (id),
    CONSTRAINT fk_character_image_asset_reference
        FOREIGN KEY (source_reference_asset_id) REFERENCES character_image_asset (id)
);

CREATE INDEX idx_character_image_asset_book_status
    ON character_image_asset (book_id, status);

CREATE INDEX idx_character_image_asset_character_created
    ON character_image_asset (character_id, created_at);

CREATE TABLE book_character_image_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    book_id BIGINT NOT NULL,
    active_reference_asset_id BIGINT,
    reference_character_id BIGINT,
    reference_version INTEGER NOT NULL DEFAULT 0,
    reference_status VARCHAR(30) NOT NULL DEFAULT 'NONE',
    model VARCHAR(80),
    base_style_prompt_hash VARCHAR(64),
    book_prompt_hash VARCHAR(64),
    approved_at DATETIME(6),
    approved_by VARCHAR(120),
    PRIMARY KEY (id),
    CONSTRAINT uk_book_character_image_profile_book UNIQUE (book_id),
    CONSTRAINT fk_book_character_image_profile_book
        FOREIGN KEY (book_id) REFERENCES book (id),
    CONSTRAINT fk_book_character_image_profile_reference_asset
        FOREIGN KEY (active_reference_asset_id) REFERENCES character_image_asset (id),
    CONSTRAINT fk_book_character_image_profile_reference_character
        FOREIGN KEY (reference_character_id) REFERENCES book_character (id)
);
