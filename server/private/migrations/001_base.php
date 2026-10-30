<?php
declare(strict_types=1);
// Immutable migration. MySQL 8.4/InnoDB; session timestamps are UTC.
return [
    <<<'SQL'
CREATE TABLE app_state (
    id TINYINT UNSIGNED PRIMARY KEY,
    server_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    radio_revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CHECK(id=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,
    <<<'SQL'
CREATE TABLE locations (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    latitude DECIMAL(9,6) NOT NULL,
    longitude DECIMAL(9,6) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CHECK(latitude BETWEEN -90 AND 90),
    CHECK(longitude BETWEEN -180 AND 180)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,
    <<<'SQL'
CREATE TABLE admin_users (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    login VARCHAR(128) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auth_revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    last_login_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,
    <<<'SQL'
CREATE TABLE clients (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    token_hash BINARY(32) NULL UNIQUE,
    scopes JSON NOT NULL,
    auth_revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    app_version VARCHAR(40) NULL,
    last_seen_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CHECK(JSON_TYPE(scopes)='ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,
    <<<'SQL'
CREATE TABLE radio_stations (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    stream_url TEXT NOT NULL,
    genres JSON NOT NULL,
    country_code CHAR(2) NOT NULL,
    homepage TEXT NULL,
    codec VARCHAR(32) NULL,
    bitrate_kbps INT UNSIGNED NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX(enabled,name),
    CHECK(JSON_TYPE(genres)='ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,
    <<<'SQL'
CREATE TABLE client_settings (
    client_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    location_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    selected_station_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    preferences JSON NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    finance_list_revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    favorites_revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    FOREIGN KEY(client_id) REFERENCES clients(id) ON DELETE RESTRICT,
    FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    FOREIGN KEY(selected_station_id) REFERENCES radio_stations(id) ON DELETE RESTRICT,
    CHECK(JSON_TYPE(preferences)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,
    <<<'SQL'
CREATE TABLE pairing_codes (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    code_hash BINARY(32) NOT NULL UNIQUE,
    issued_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    expires_at DATETIME(3) NOT NULL,
    used_at DATETIME(3) NULL,
    issued_scopes JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY(issued_by) REFERENCES admin_users(id) ON DELETE RESTRICT,
    INDEX(expires_at),
    CHECK(JSON_TYPE(issued_scopes)='ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,
    <<<'SQL'
CREATE TABLE weather_snapshots (
    location_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    payload JSON NULL,
    data_as_of DATETIME(3) NULL,
    fetched_at DATETIME(3) NULL,
    valid_until DATETIME(3) NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    location_revision BIGINT UNSIGNED NOT NULL,
    test_data BOOLEAN NOT NULL DEFAULT FALSE,
    last_attempt_at DATETIME(3) NULL,
    last_error_code VARCHAR(64) NULL,
    FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    CHECK(payload IS NULL OR JSON_TYPE(payload)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,
    <<<'SQL'
CREATE TABLE client_station_favorites (
    client_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    station_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sort_order INT UNSIGNED NOT NULL,
    PRIMARY KEY(client_id,station_id),
    UNIQUE(client_id,sort_order),
    FOREIGN KEY(client_id) REFERENCES clients(id) ON DELETE RESTRICT,
    FOREIGN KEY(station_id) REFERENCES radio_stations(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,
    <<<'SQL'
CREATE TABLE integrations (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    credentials_ciphertext LONGBLOB NULL,
    nonce VARBINARY(24) NULL,
    key_id VARCHAR(64) NULL,
    settings JSON NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    next_retry_at DATETIME(3) NULL,
    last_success_at DATETIME(3) NULL,
    last_error_code VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CHECK(JSON_TYPE(settings)='OBJECT'),
    CHECK((credentials_ciphertext IS NULL AND nonce IS NULL AND key_id IS NULL) OR (credentials_ciphertext IS NOT NULL AND OCTET_LENGTH(nonce)=24 AND nonce IS NOT NULL AND key_id IS NOT NULL)),
    CHECK(type NOT IN ('cbr','moex_iss') OR credentials_ciphertext IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,
    <<<'SQL'
CREATE TABLE finance_instruments (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    instrument_key VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    integration_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    type VARCHAR(16) NOT NULL,
    symbol VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    exchange VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    board VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    name VARCHAR(255) NOT NULL,
    quote_currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE(integration_id,type,symbol,exchange,board,quote_currency),
    FOREIGN KEY(integration_id) REFERENCES integrations(id) ON DELETE RESTRICT,
    CHECK(type IN ('currency','share')),
    CHECK(quote_currency='RUB'),
    CHECK((type='currency' AND exchange='' AND board='') OR (type='share' AND exchange<>'' AND board<>''))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,
    <<<'SQL'
CREATE TABLE client_finance_watchlist (
    client_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    instrument_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sort_order INT UNSIGNED NOT NULL,
    show_on_dashboard BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY(client_id,instrument_id),
    UNIQUE(client_id,sort_order),
    FOREIGN KEY(client_id) REFERENCES clients(id) ON DELETE RESTRICT,
    FOREIGN KEY(instrument_id) REFERENCES finance_instruments(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,
    <<<'SQL'
CREATE TABLE finance_quotes (
    instrument_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    instrument_revision BIGINT UNSIGNED NOT NULL,
    price DECIMAL(38,12) NULL,
    nominal DECIMAL(24,8) NOT NULL,
    previous_price DECIMAL(38,12) NULL,
    change_amount DECIMAL(38,12) NULL,
    change_pct DECIMAL(20,10) NULL,
    price_kind VARCHAR(32) NOT NULL,
    quote_as_of DATETIME(3) NULL,
    data_as_of DATETIME(3) NULL,
    effective_date DATE NULL,
    reference_date DATE NULL,
    reference_at DATETIME(3) NULL,
    comparison_basis VARCHAR(32) NOT NULL,
    source_timezone VARCHAR(64) NOT NULL,
    delay_seconds INT UNSIGNED NULL,
    market_state VARCHAR(32) NOT NULL,
    payload JSON NOT NULL,
    fetched_at DATETIME(3) NOT NULL,
    valid_until DATETIME(3) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    test_data BOOLEAN NOT NULL DEFAULT FALSE,
    last_attempt_at DATETIME(3) NULL,
    last_error_code VARCHAR(64) NULL,
    FOREIGN KEY(instrument_id) REFERENCES finance_instruments(id) ON DELETE RESTRICT,
    CHECK(nominal>0),
    CHECK(price IS NULL OR price>=0),
    CHECK(previous_price IS NULL OR previous_price>=0),
    CHECK(price_kind IN ('official_rate','last_trade','none')),
    CHECK((price IS NULL AND price_kind='none') OR (price IS NOT NULL AND price_kind<>'none')),
    CHECK(JSON_TYPE(payload)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,
    <<<'SQL'
CREATE TABLE audit_events (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    admin_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    client_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    action VARCHAR(64) NOT NULL,
    object_type VARCHAR(32) NOT NULL,
    object_id VARCHAR(128) NOT NULL,
    changes JSON NOT NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY(admin_id) REFERENCES admin_users(id) ON DELETE RESTRICT,
    FOREIGN KEY(client_id) REFERENCES clients(id) ON DELETE RESTRICT,
    INDEX(occurred_at),
    CHECK(JSON_TYPE(changes)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,
    <<<'SQL'
CREATE TABLE jobs (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    queue VARCHAR(16) NOT NULL,
    type VARCHAR(64) NOT NULL,
    dedupe_key VARCHAR(191) NULL UNIQUE,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    run_after DATETIME(3) NOT NULL,
    lease_until DATETIME(3) NULL,
    lease_owner CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    attempts INT UNSIGNED NOT NULL DEFAULT 0,
    last_error_code VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX(queue,status,run_after),
    CHECK(JSON_TYPE(payload)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,
];
