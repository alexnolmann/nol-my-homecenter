<?php
declare(strict_types=1);
return [
    'environment' => 'production',
    'fixtures' => false,
    'transport' => ['http_host' => getenv('MC_HTTP_HOST') ?: null],
    'server_id' => getenv('SERVER_ID') ?: null,
    'features' => ['weather' => true, 'radio' => true, 'finance' => true, 'home' => false],
    'development' => ['token' => null, 'trusted_proxy' => false],
    'database' => ['host' => getenv('DB_HOST') ?: 'mysql', 'port' => (int) (getenv('DB_PORT') ?: 3306),
        'name' => getenv('DB_NAME') ?: null, 'user' => getenv('DB_USER') ?: null, 'password' => getenv('DB_PASSWORD') ?: null],
];
