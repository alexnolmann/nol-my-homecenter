<?php
declare(strict_types=1);
namespace MediaCentre\Storage;
use MediaCentre\Shared\AppConfig;
final class Connection
{
    public static function open(AppConfig $config, ?string $database=null): \PDO
    {
        $db=$config->values['database']; $name=$database ?? $db['name'];
        if (!is_string($name) || !preg_match('/^[A-Za-z0-9_-]{1,64}$/D',$name) || !preg_match('/^[A-Za-z0-9_.-]+$/D',$db['host']) || $db['port']<1 || $db['port']>65535) throw new \RuntimeException('Database configuration invalid.');
        $pdo=new \PDO('mysql:host='.$db['host'].';port='.$db['port'].';dbname='.$name.';charset=utf8mb4',$db['user'],$db['password'],[\PDO::ATTR_ERRMODE=>\PDO::ERRMODE_EXCEPTION,\PDO::ATTR_DEFAULT_FETCH_MODE=>\PDO::FETCH_ASSOC,\PDO::ATTR_EMULATE_PREPARES=>false,\PDO::ATTR_TIMEOUT=>5]);
        $pdo->exec("SET time_zone = '+00:00'");
        $pdo->exec('SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ');
        $pdo->exec("SET SESSION sql_mode = 'STRICT_TRANS_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'");
        return $pdo;
    }
}
