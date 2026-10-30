<?php
declare(strict_types=1);
namespace MediaCentre\Storage;
final class Migrator
{
    public function __construct(private \PDO $pdo,private string $directory){}
    public function migrate(): array
    {
        if (!str_starts_with((string)$this->pdo->query('SELECT VERSION()')->fetchColumn(),'8.4.')) throw new \RuntimeException('Migrations require MySQL 8.4.');
        $name='mc-migrate-'.substr(hash('sha256',(string)$this->pdo->query('SELECT DATABASE()')->fetchColumn()),0,32);
        $lock=$this->pdo->prepare('SELECT GET_LOCK(?,10)');$lock->execute([$name]);
        if ((int)$lock->fetchColumn()!==1) throw new \RuntimeException('Migration lock unavailable.');
        try {
            $this->pdo->exec('CREATE TABLE IF NOT EXISTS schema_migrations (version VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY, checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, state VARCHAR(16) NOT NULL, applied_at DATETIME(3) NULL) ENGINE=InnoDB');
            $rows=$this->pdo->query('SELECT * FROM schema_migrations')->fetchAll();$existing=array_column($rows,null,'version');
            $files=glob($this->directory.'/*.php');sort($files);$applied=[];
            foreach ($files as $file) {
                $version=basename($file);$checksum=hash_file('sha256',$file);
                if (isset($existing[$version])) {
                    if ($existing[$version]['checksum']!==$checksum || $existing[$version]['state']!=='applied') throw new \RuntimeException('Changed or incomplete migration; inspect before continuing.');
                    continue;
                }
                $migration=require $file;
                foreach ($migration as $statement) {
                    if (!preg_match('/^CREATE TABLE ([a-z_]+)/',$statement,$match)) throw new \RuntimeException('Unsupported migration statement.');
                    $check=$this->pdo->prepare('SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?');$check->execute([$match[1]]);
                    if ($check->fetchColumn()) throw new \RuntimeException('Existing unmanaged table conflicts with migration.');
                }
                $insert=$this->pdo->prepare("INSERT INTO schema_migrations(version,checksum,state) VALUES(?,?,'applying')");$insert->execute([$version,$checksum]);
                // MySQL DDL commits implicitly. Never report a partially applied migration as successful.
                foreach ($migration as $statement) $this->pdo->exec($statement);
                $finish=$this->pdo->prepare("UPDATE schema_migrations SET state='applied',applied_at=UTC_TIMESTAMP(3) WHERE version=?");$finish->execute([$version]);
                $applied[]=$version;
            }
            return $applied;
        } finally {$release=$this->pdo->prepare('SELECT RELEASE_LOCK(?)');$release->execute([$name]);}
    }
}
