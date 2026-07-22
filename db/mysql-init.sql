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
    `phone` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
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

CREATE TABLE IF NOT EXISTS walk_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(40) NOT NULL,
    place_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    city_code VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_walk_group_place_name (place_id, name),
    UNIQUE KEY uk_walk_group_place_owner (place_id, owner_id),
    KEY idx_walk_group_city (city_code),
    CONSTRAINT fk_walk_group_place FOREIGN KEY (place_id) REFERENCES pet_friendly_place(id) ON DELETE CASCADE,
    CONSTRAINT fk_walk_group_owner FOREIGN KEY (owner_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS walk_group_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_walk_group_member (group_id, user_id),
    CONSTRAINT fk_walk_member_group FOREIGN KEY (group_id) REFERENCES walk_group(id) ON DELETE CASCADE,
    CONSTRAINT fk_walk_member_user FOREIGN KEY (user_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS walk_group_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_walk_message_group_id (group_id, id),
    CONSTRAINT fk_walk_message_group FOREIGN KEY (group_id) REFERENCES walk_group(id) ON DELETE CASCADE,
    CONSTRAINT fk_walk_message_sender FOREIGN KEY (sender_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dictionary_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dict_type VARCHAR(40) NOT NULL,
    item_code VARCHAR(40) NOT NULL,
    label VARCHAR(80) NOT NULL,
    parent_code VARCHAR(40) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dictionary_type_code (dict_type, item_code),
    KEY idx_dictionary_type_parent (dict_type, parent_code, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_pet (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    species VARCHAR(20) NOT NULL,
    name VARCHAR(64) NOT NULL,
    breed_code VARCHAR(40) NOT NULL,
    gender_code VARCHAR(20) NOT NULL,
    neutered BIT(1) NOT NULL DEFAULT b'0',
    birth_date DATE NULL,
    avatar_data LONGBLOB NULL,
    avatar_content_type VARCHAR(64) NULL,
    PRIMARY KEY (id),
    KEY idx_user_pet_owner (owner_id),
    CONSTRAINT fk_user_pet_owner FOREIGN KEY (owner_id) REFERENCES user_account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO user_account (username, password, role, nickname)
SELECT 'admin', '$2a$10$wOO7/BKNhz3NJIOCK3bSSOY8E2aT/xrNZafL6gTURKlQ/NJNR55DO', 'ROLE_ADMIN', 'admin'
WHERE NOT EXISTS (
    SELECT 1 FROM user_account WHERE username = 'admin'
);
