<?php
declare(strict_types=1);
if (PHP_SAPI!=='cli') exit(1);
$root=dirname(__DIR__,2);$config=require $root.'/server/private/bootstrap.php';
try {
    $pdo=MediaCentre\Storage\Connection::open($config);
    $applied=(new MediaCentre\Storage\Migrator($pdo,$root.'/server/private/migrations'))->migrate();
    echo 'Migrations applied: '.count($applied)."\n";
} catch (Throwable $error) {echo 'Migration failed ('.get_class($error).'); credentials/SQL diagnostics suppressed; inspect schema_migrations state.' . "\n";exit(1);}
