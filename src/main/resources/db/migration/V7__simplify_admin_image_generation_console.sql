ALTER TABLE character_image_asset
    ADD COLUMN slot_no INTEGER NULL AFTER reference_version;

CREATE UNIQUE INDEX uk_character_image_asset_book_role_slot
    ON character_image_asset (book_id, asset_role, slot_no);

CREATE INDEX idx_character_image_asset_book_role
    ON character_image_asset (book_id, asset_role);
