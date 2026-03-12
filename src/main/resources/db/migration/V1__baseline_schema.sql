CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    email VARCHAR(100) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    provider VARCHAR(10) NOT NULL,
    provider_uid VARCHAR(120) NOT NULL,
    profile_img_url VARCHAR(255),
    is_admin BIT NOT NULL DEFAULT 0,
    jwt_refresh_token CHAR(128),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_provider_uid UNIQUE (provider_uid)
);

CREATE TABLE book (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    title VARCHAR(200) NOT NULL,
    author VARCHAR(120) NOT NULL,
    language VARCHAR(10) NOT NULL,
    is_default BIT NOT NULL,
    summary BIT NOT NULL,
    cover_img_url VARCHAR(255),
    summary_url VARCHAR(255),
    epub_path VARCHAR(255),
    normalization_status VARCHAR(30),
    rule_version VARCHAR(50),
    locator_version VARCHAR(50),
    normalized_artifact_path VARCHAR(255),
    uploaded_by_user_id BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_book_uploaded_by_user
        FOREIGN KEY (uploaded_by_user_id) REFERENCES users (id)
);

CREATE TABLE chapter (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    book_id BIGINT NOT NULL,
    idx INTEGER NOT NULL,
    title VARCHAR(200),
    spine_href VARCHAR(255),
    paragraph_count INTEGER,
    paragraph_starts_json TEXT,
    paragraph_lengths_json TEXT,
    total_code_points INTEGER,
    page_start INTEGER NOT NULL,
    page_end INTEGER NOT NULL,
    start_pos INTEGER NOT NULL,
    end_pos INTEGER NOT NULL,
    raw_text TEXT,
    summary_text TEXT,
    summary_upload_url VARCHAR(255),
    pov_summaries_cached BIT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chapter_book
        FOREIGN KEY (book_id) REFERENCES book (id)
);

CREATE UNIQUE INDEX uk_chapter_book_idx ON chapter (book_id, idx);

CREATE TABLE book_character (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    character_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    names VARCHAR(255),
    profile_image VARCHAR(255),
    image_generation_status VARCHAR(20),
    is_main_character BIT NOT NULL,
    first_chapter_idx INTEGER NOT NULL,
    personality_text TEXT,
    profile_text TEXT,
    embedding_vector LONGBLOB,
    PRIMARY KEY (id),
    CONSTRAINT fk_book_character_book
        FOREIGN KEY (book_id) REFERENCES book (id)
);

CREATE TABLE book_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    event_id VARCHAR(50) NOT NULL,
    start_block_index INTEGER,
    start_offset INTEGER,
    end_block_index INTEGER,
    end_offset INTEGER,
    start_txt_offset INTEGER NOT NULL,
    end_txt_offset INTEGER NOT NULL,
    raw_text TEXT NOT NULL,
    idx INTEGER NOT NULL,
    chapter_id BIGINT,
    book_id BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_book_event_chapter
        FOREIGN KEY (chapter_id) REFERENCES chapter (id),
    CONSTRAINT fk_book_event_book
        FOREIGN KEY (book_id) REFERENCES book (id)
);

CREATE TABLE favorite (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_favorite_user_book UNIQUE (user_id, book_id),
    CONSTRAINT fk_favorite_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_favorite_book
        FOREIGN KEY (book_id) REFERENCES book (id)
);

CREATE TABLE user_read_state (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    last_locator_json TEXT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_read_state_user_book UNIQUE (user_id, book_id),
    CONSTRAINT fk_user_read_state_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_read_state_book
        FOREIGN KEY (book_id) REFERENCES book (id)
);

CREATE TABLE bookmark (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    start_locator_json TEXT NOT NULL,
    end_locator_json TEXT,
    start_txt_offset INTEGER NOT NULL,
    end_txt_offset INTEGER,
    locator_version VARCHAR(50),
    color VARCHAR(7),
    memo VARCHAR(1000),
    PRIMARY KEY (id),
    CONSTRAINT fk_bookmark_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bookmark_book
        FOREIGN KEY (book_id) REFERENCES book (id)
);

CREATE INDEX idx_bookmark_user_book ON bookmark (user_id, book_id);
CREATE INDEX idx_bookmark_book_created_at ON bookmark (book_id, created_at);

CREATE TABLE event_relationship_edge (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    from_char_id BIGINT,
    to_char_id BIGINT,
    event_id BIGINT,
    explanation TEXT,
    relation_tags JSON,
    interaction_count INTEGER,
    sentiment_score FLOAT,
    sentiment_label VARCHAR(255),
    edge_color_hex VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT fk_event_relationship_edge_from_character
        FOREIGN KEY (from_char_id) REFERENCES book_character (id),
    CONSTRAINT fk_event_relationship_edge_to_character
        FOREIGN KEY (to_char_id) REFERENCES book_character (id),
    CONSTRAINT fk_event_relationship_edge_event
        FOREIGN KEY (event_id) REFERENCES book_event (id)
);

CREATE TABLE event_character_stat (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    event_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    node_weight DOUBLE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_event_character_stat_event_character UNIQUE (event_id, character_id),
    CONSTRAINT fk_event_character_stat_event
        FOREIGN KEY (event_id) REFERENCES book_event (id),
    CONSTRAINT fk_event_character_stat_character
        FOREIGN KEY (character_id) REFERENCES book_character (id)
);

CREATE TABLE chapter_character_stat (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    chapter_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    mention_count INTEGER NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chapter_character_stat_chapter_character UNIQUE (chapter_id, character_id),
    CONSTRAINT fk_chapter_character_stat_chapter
        FOREIGN KEY (chapter_id) REFERENCES chapter (id),
    CONSTRAINT fk_chapter_character_stat_character
        FOREIGN KEY (character_id) REFERENCES book_character (id)
);

CREATE TABLE character_pov_summary (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    summary_text TEXT,
    book_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_character_pov_summary_book
        FOREIGN KEY (book_id) REFERENCES book (id),
    CONSTRAINT fk_character_pov_summary_chapter
        FOREIGN KEY (chapter_id) REFERENCES chapter (id),
    CONSTRAINT fk_character_pov_summary_character
        FOREIGN KEY (character_id) REFERENCES book_character (id)
);

CREATE TABLE chapter_relationship_edge (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    book_id BIGINT NOT NULL,
    chapter_idx INTEGER NOT NULL,
    from_char_id BIGINT NOT NULL,
    to_char_id BIGINT NOT NULL,
    cumulative_interaction INTEGER NOT NULL,
    sentiment_weighted_sum FLOAT NOT NULL,
    edge_color_hex VARCHAR(7),
    edge_width FLOAT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chapter_relationship_edge_book
        FOREIGN KEY (book_id) REFERENCES book (id),
    CONSTRAINT fk_chapter_relationship_edge_from_character
        FOREIGN KEY (from_char_id) REFERENCES book_character (id),
    CONSTRAINT fk_chapter_relationship_edge_to_character
        FOREIGN KEY (to_char_id) REFERENCES book_character (id)
);

CREATE UNIQUE INDEX uk_chapter_relationship_edge_scope
    ON chapter_relationship_edge (book_id, chapter_idx, from_char_id, to_char_id);
