<?php
declare(strict_types=1);
return [<<<'SQL'
CREATE TABLE photoframe_settings (
    id TINYINT UNSIGNED PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    CHECK(id=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL, <<<'SQL'
CREATE TABLE photoframe_photos (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    sort_order INT UNSIGNED NOT NULL,
    full_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    thumb_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    width SMALLINT UNSIGNED NOT NULL,
    height SMALLINT UNSIGNED NOT NULL,
    bytes INT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX(sort_order,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL];
