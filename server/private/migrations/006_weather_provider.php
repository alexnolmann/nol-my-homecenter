<?php
declare(strict_types=1);
return [<<<'SQL'
CREATE TABLE weather_provider_settings (
    id TINYINT UNSIGNED PRIMARY KEY,
    provider VARCHAR(32) NOT NULL DEFAULT 'open_meteo',
    credentials_ciphertext LONGBLOB NULL,
    nonce VARBINARY(24) NULL,
    key_id VARCHAR(64) NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CHECK(id=1),
    CHECK(provider IN ('open_meteo','visual_crossing','met_norway')),
    CHECK((credentials_ciphertext IS NULL AND nonce IS NULL AND key_id IS NULL) OR (credentials_ciphertext IS NOT NULL AND OCTET_LENGTH(nonce)=24 AND nonce IS NOT NULL AND key_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL];
