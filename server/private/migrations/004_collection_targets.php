<?php
declare(strict_types=1);
return [<<<'SQL'
CREATE TABLE collection_targets (
    target_key VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    kind VARCHAR(16) NOT NULL,
    object_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    configuration_hash BINARY(32) NOT NULL,
    context JSON NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    next_due_at DATETIME(3) NOT NULL,
    failure_count INT UNSIGNED NOT NULL DEFAULT 0,
    last_attempt_at DATETIME(3) NULL,
    last_success_at DATETIME(3) NULL,
    last_error_code VARCHAR(64) NULL,
    last_result VARCHAR(16) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    FOREIGN KEY(job_id) REFERENCES jobs(id) ON DELETE RESTRICT,
    INDEX(enabled,job_id,next_due_at),
    CHECK(kind IN ('weather','cbr','moex')),
    CHECK(JSON_TYPE(context)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL];
