CREATE DATABASE IF NOT EXISTS wu_jia_you_chong
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE wu_jia_you_chong;

CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    nickname VARCHAR(64),
    avatar_url VARCHAR(512),
    avatar_data LONGBLOB,
    avatar_content_type VARCHAR(64),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pet_friendly_place (
                                                  `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
    `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
    `address` varchar(240) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `latitude` double DEFAULT NULL,
    `longitude` double DEFAULT NULL,
    `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `tags` varchar(800) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `indoor_allowed` bit(1) NOT NULL DEFAULT b'0',
    `large_dog_friendly` bit(1) NOT NULL DEFAULT b'0',
    `cat_friendly` bit(1) NOT NULL DEFAULT b'0',
    `leash_required` bit(1) NOT NULL DEFAULT b'0',
    `water_available` bit(1) NOT NULL DEFAULT b'0',
    `parking_available` bit(1) NOT NULL DEFAULT b'0',
    `fee_required` bit(1) NOT NULL DEFAULT b'0',
    `policy_note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `uploaded_by_id` bigint(20) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_place_uploaded_by_id` (`uploaded_by_id`),
    KEY `idx_place_type` (`type`),
    CONSTRAINT `fk_place_uploaded_by` FOREIGN KEY (`uploaded_by_id`) REFERENCES `user_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS place_comment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    place_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating INT NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL,
    image_content_type VARCHAR(100),
    image_data LONGBLOB,
    PRIMARY KEY (id),
    KEY idx_comment_place_id (place_id),
    KEY idx_comment_user_id (user_id),
    CONSTRAINT fk_comment_place
        FOREIGN KEY (place_id) REFERENCES pet_friendly_place (id),
    CONSTRAINT fk_comment_user
        FOREIGN KEY (user_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO user_account (username, password, role, nickname)
SELECT 'admin', '$2a$10$wOO7/BKNhz3NJIOCK3bSSOY8E2aT/xrNZafL6gTURKlQ/NJNR55DO', 'ROLE_ADMIN', 'admin'
WHERE NOT EXISTS (
    SELECT 1 FROM user_account WHERE username = 'admin'
);
