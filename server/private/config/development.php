<?php
declare(strict_types=1);
return [
    'environment' => 'development',
    'fixtures' => true,
    'transport' => ['http_host' => getenv('MC_HTTP_HOST') ?: null],
    'server_id' => '00000000-0000-4000-8000-000000000001',
    'features' => ['weather' => true, 'radio' => true, 'finance' => true, 'home' => false],
    'development' => ['token' => getenv('MC_DEV_TOKEN') ?: null, 'trusted_proxy' => false, 'client_id'=>'20000000-0000-4000-8000-000000000200'],
    'database' => ['host' => getenv('DB_HOST') ?: '127.0.0.1', 'port' => (int) (getenv('DB_PORT') ?: 3306),
        'name' => getenv('DB_NAME') ?: null, 'user' => getenv('DB_USER') ?: null, 'password' => getenv('DB_PASSWORD') ?: null],
];
