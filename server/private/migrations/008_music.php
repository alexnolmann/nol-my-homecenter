<?php
declare(strict_types=1);
return [<<<'SQL'
CREATE TABLE music_libraries (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 name VARCHAR(80) NOT NULL,
 root_path VARCHAR(2048) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
 generation BIGINT UNSIGNED NOT NULL DEFAULT 0,
 scan_generation BIGINT UNSIGNED NOT NULL DEFAULT 0,
 scan_state JSON NULL,
 scan_status VARCHAR(20) NOT NULL DEFAULT 'queued',
 scanned_files INT UNSIGNED NOT NULL DEFAULT 0,
 skipped_files INT UNSIGNED NOT NULL DEFAULT 0,
 error_code VARCHAR(40) NULL,
 scanned_at DATETIME(3) NULL,
 requested_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,<<<'SQL'
CREATE TABLE music_entries (
 library_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 generation BIGINT UNSIGNED NOT NULL,
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 parent_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 kind VARCHAR(6) NOT NULL,
 relative_path VARCHAR(2048) COLLATE utf8mb4_bin NOT NULL,
 name VARCHAR(255) NOT NULL,
 artist VARCHAR(255) NULL,
 album VARCHAR(255) NULL,
 bytes BIGINT UNSIGNED NOT NULL DEFAULT 0,
 modified BIGINT UNSIGNED NOT NULL DEFAULT 0,
 indexed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(library_id,generation,id),
 INDEX music_parent(library_id,generation,parent_id,kind),
 INDEX music_track(id,generation),
 FOREIGN KEY(library_id) REFERENCES music_libraries(id) ON DELETE CASCADE,
 CHECK(kind IN ('folder','track'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL];
