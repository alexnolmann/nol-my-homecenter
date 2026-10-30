<?php
declare(strict_types=1);

// Compatibility response only; this is not an authenticated application API.
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');

echo json_encode([
    'data' => [
        'probe' => 'mediacentre-h0',
        'message' => 'PHP JSON доступен',
        'php_version' => PHP_VERSION,
        'server_time' => gmdate('Y-m-d\TH:i:s\Z'),
    ],
    'meta' => ['api_version' => 1, 'compatibility_probe' => true],
], JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
