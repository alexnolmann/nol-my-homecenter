<?php
declare(strict_types=1);
return [<<<'SQL'
CREATE TABLE client_climate_settings (
    client_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    poll_minutes SMALLINT UNSIGNED NOT NULL DEFAULT 15,
    initialized BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    FOREIGN KEY(client_id) REFERENCES clients(id) ON DELETE CASCADE,
    CHECK(poll_minutes BETWEEN 1 AND 120)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL,<<<'SQL'
CREATE TABLE climate_sensors (
    client_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    address CHAR(17) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(40) NOT NULL,
    position TINYINT UNSIGNED NOT NULL,
    temperature DECIMAL(5,2) NULL,
    humidity TINYINT UNSIGNED NULL,
    battery_millivolts SMALLINT UNSIGNED NULL,
    measured_at BIGINT UNSIGNED NULL,
    received_at DATETIME(3) NULL,
    last_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    PRIMARY KEY(client_id,address),
    FOREIGN KEY(client_id) REFERENCES client_climate_settings(client_id) ON DELETE CASCADE,
    CHECK(position BETWEEN 0 AND 2),
    CHECK(temperature BETWEEN -50 AND 85),
    CHECK(humidity BETWEEN 0 AND 100),
    CHECK(battery_millivolts BETWEEN 1500 AND 4000),
    CHECK(last_status IN ('pending','ok','unavailable','bluetooth_off'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL];
