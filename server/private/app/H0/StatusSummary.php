<?php
declare(strict_types=1);

namespace MediaCentre\H0;

/** Reads an allowlist of diagnostic facts; never loads DB credentials or raw HTTP/log data. */
final class StatusSummary
{
    public static function read(string $root): array
    {
        $load = static function (string $path): array {
            if (!is_file($path) || filesize($path) > 1024 * 1024) return [];
            try { return json_decode(file_get_contents($path), true, 64, JSON_THROW_ON_ERROR); }
            catch (\Throwable) { return []; }
        };
        $providers = $load($root . '/.local/h0/providers/results.json');
        $host = $load($root . '/.local/h0/host-results.json');
        return [
            'provider_checked_at' => $providers['checked_at'] ?? null,
            'weather_ok' => isset($providers['weather']['forecast']['daily']),
            'currencies_ok' => count($providers['currencies']['rates'] ?? []) === 3,
            'shares_ok' => count($providers['shares'] ?? []) === 3 && !isset($providers['shares']['error']),
            'cbr_effective_date' => $providers['currencies']['effective_date'] ?? null,
            'mysql_connected' => $host['mysql']['connected'] ?? false,
            'mysql_version' => $host['mysql']['server_version'] ?? null,
            'host_checked_at' => $host['checked_at'] ?? null,
        ];
    }
}
