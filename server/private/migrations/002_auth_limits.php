<?php
declare(strict_types=1);
return [<<<'SQL'
CREATE TABLE auth_limits (
    bucket_hash BINARY(32) PRIMARY KEY,
    window_started BIGINT UNSIGNED NOT NULL,
    attempts INT UNSIGNED NOT NULL,
    INDEX(window_started)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL];
