<?php
declare(strict_types=1);
return [<<<'SQL'
CREATE TABLE pairing_profiles (
    pairing_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    location_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    template_client_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    admin_auth_revision BIGINT UNSIGNED NOT NULL,
    FOREIGN KEY(pairing_id) REFERENCES pairing_codes(id) ON DELETE RESTRICT,
    FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE RESTRICT,
    FOREIGN KEY(template_client_id) REFERENCES clients(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
SQL];
