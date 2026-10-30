<?php
declare(strict_types=1);
namespace MediaCentre\Shared;

final class Readiness
{
    public static function check(AppConfig $config, string $storage): array
    {
        $extensions = [];
        foreach (['pdo_mysql','curl','sodium','xml','simplexml','bcmath'] as $name) $extensions[$name] = extension_loaded($name);
        $database = false;
        try {
            $db = $config->values['database'];
            if (!preg_match('/^[A-Za-z0-9_.-]+$/D', $db['host']) || !preg_match('/^[A-Za-z0-9_-]+$/D', $db['name'] ?? '')) throw new \RuntimeException('Database not configured.');
            $pdo = new \PDO('mysql:host=' . $db['host'] . ';port=' . $db['port'] . ';dbname=' . $db['name'] . ';charset=utf8mb4', $db['user'], $db['password'], [\PDO::ATTR_ERRMODE=>\PDO::ERRMODE_EXCEPTION, \PDO::ATTR_TIMEOUT=>3]);
            $database = (int) $pdo->query('SELECT 1')->fetchColumn() === 1;
        } catch (\Throwable) { /* Do not expose DSN, credentials or driver diagnostics. */ }
        $heartbeat = false;
        $file = $storage . '/maintenance.json';
        if (is_file($file)) {
            $value = json_decode((string) file_get_contents($file), true);
            $timestamp = strtotime($value['updated_at'] ?? '');
            $heartbeat = $timestamp !== false && time() - $timestamp >= 0 && time() - $timestamp <= 45;
        }
        return ['ready'=>!in_array(false, $extensions, true) && $database && $heartbeat, 'extensions'=>$extensions, 'database'=>$database, 'maintenance'=>$heartbeat];
    }
}
