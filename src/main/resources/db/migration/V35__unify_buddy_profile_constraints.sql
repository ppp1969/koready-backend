ALTER TABLE buddy_profiles
    CHANGE COLUMN nationality nationality_code CHAR(2) NOT NULL,
    ADD CONSTRAINT chk_buddy_profile_nationality_code
        CHECK (nationality_code REGEXP '^[A-Z]{2}$'),
    ADD CONSTRAINT chk_buddy_profile_bio_length
        CHECK (bio IS NULL OR CHAR_LENGTH(bio) <= 120);

ALTER TABLE user_travel_styles
    DROP CHECK chk_user_travel_style_order,
    ADD CONSTRAINT chk_user_travel_style_order
        CHECK (display_order BETWEEN 1 AND 7);

ALTER TABLE buddy_social_links
    ADD CONSTRAINT chk_buddy_social_link_order_limit
        CHECK (display_order BETWEEN 1 AND 2);

CREATE TABLE buddy_profile_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    declared_size BIGINT NOT NULL,
    actual_size BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_buddy_profile_image_public_id UNIQUE (public_id),
    CONSTRAINT uq_buddy_profile_image_object_key UNIQUE (object_key),
    CONSTRAINT fk_buddy_profile_image_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_buddy_profile_image_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT chk_buddy_profile_image_declared_size
        CHECK (declared_size BETWEEN 1 AND 5242880),
    CONSTRAINT chk_buddy_profile_image_actual_size
        CHECK (actual_size IS NULL OR actual_size BETWEEN 1 AND 5242880),
    CONSTRAINT chk_buddy_profile_image_status
        CHECK (status IN ('PENDING', 'READY')),
    CONSTRAINT chk_buddy_profile_image_completion
        CHECK (
            (status = 'PENDING' AND actual_size IS NULL AND completed_at IS NULL)
            OR
            (status = 'READY' AND actual_size IS NOT NULL AND completed_at IS NOT NULL)
        )
);

CREATE INDEX idx_buddy_profile_image_owner
    ON buddy_profile_images (user_id, status, id);

ALTER TABLE buddy_profiles
    ADD CONSTRAINT chk_buddy_profile_image_url
        CHECK (
            profile_image_url IS NULL
            OR profile_image_url REGEXP '^/api/v1/profile-images/img_[0-9a-f]{32}$'
        );
