CREATE TABLE buddy_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    profile_image_url VARCHAR(2048) NULL,
    nickname VARCHAR(30) NOT NULL,
    nationality VARCHAR(100) NULL,
    korean_level VARCHAR(20) NOT NULL,
    bio VARCHAR(500) NULL,
    profile_public BOOLEAN NOT NULL,
    sns_public BOOLEAN NOT NULL,
    allows_messages BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_buddy_profile_user UNIQUE (user_id),
    CONSTRAINT fk_buddy_profile_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_buddy_profile_korean_level CHECK (
        korean_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')
    )
);

CREATE TABLE buddy_profile_languages (
    profile_id BIGINT NOT NULL,
    language_code VARCHAR(10) NOT NULL,
    display_order TINYINT NOT NULL,
    PRIMARY KEY (profile_id, language_code),
    CONSTRAINT uq_buddy_profile_language_order UNIQUE (profile_id, display_order),
    CONSTRAINT fk_buddy_profile_language_profile
        FOREIGN KEY (profile_id) REFERENCES buddy_profiles (id) ON DELETE CASCADE,
    CONSTRAINT chk_buddy_profile_language CHECK (language_code IN ('KO', 'EN')),
    CONSTRAINT chk_buddy_profile_language_order CHECK (display_order BETWEEN 1 AND 2)
);

CREATE TABLE buddy_profile_styles (
    profile_id BIGINT NOT NULL,
    buddy_style VARCHAR(40) NOT NULL,
    display_order TINYINT NOT NULL,
    PRIMARY KEY (profile_id, buddy_style),
    CONSTRAINT uq_buddy_profile_style_order UNIQUE (profile_id, display_order),
    CONSTRAINT fk_buddy_profile_style_profile
        FOREIGN KEY (profile_id) REFERENCES buddy_profiles (id) ON DELETE CASCADE,
    CONSTRAINT chk_buddy_profile_style CHECK (buddy_style IN (
        'TRADITIONAL_CULTURE', 'CAFE_TOUR', 'FOODIE',
        'PHOTOGRAPHY', 'HANOK_EXPERIENCE', 'QUIET_TRAVEL'
    )),
    CONSTRAINT chk_buddy_profile_style_order CHECK (display_order BETWEEN 1 AND 6)
);

CREATE TABLE buddy_social_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    link_type VARCHAR(30) NOT NULL,
    link_value VARCHAR(200) NOT NULL,
    display_order INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_buddy_social_link_order UNIQUE (profile_id, display_order),
    CONSTRAINT fk_buddy_social_link_profile
        FOREIGN KEY (profile_id) REFERENCES buddy_profiles (id) ON DELETE CASCADE,
    CONSTRAINT chk_buddy_social_link_type CHECK (
        link_type IN ('INSTAGRAM', 'KAKAOTALK', 'THREADS', 'TIKTOK', 'ETC')
    ),
    CONSTRAINT chk_buddy_social_link_order CHECK (display_order > 0)
);
