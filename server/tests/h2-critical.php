<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';$db=new MediaCentre\Storage\Database($pdo);
$uuid=fn()=>MediaCentre\Shared\Uuid::random();$location=$uuid();$client=$uuid();$a=$uuid();$b=$uuid();$c=$uuid();
(new MediaCentre\Weather\LocationRepository($db))->create($location,'transaction test','59.938630','30.314130','Europe/Moscow');
(new MediaCentre\Client\ClientRepository($db))->create($client,'concurrency test',$location);
$stations=new MediaCentre\Radio\StationRepository($db);
foreach([$a,$b,$c] as $id)$stations->create($id,'atomic list','https://example.invalid/test.mp3',[],'RU');
$lists=new MediaCentre\Client\OrderedListRepository($db);$lists->favorites($client,1,[$a,$b]);
$before=$lists->readFavorites($client);$auditBefore=(int)$pdo->query('SELECT COUNT(*) FROM audit_events')->fetchColumn();
try{
    $db->transaction(function()use($db,$client,$a,$b,$c):void{
        $db->lockSettings($client,2,'favorites_revision');
        $db->query('DELETE FROM client_station_favorites WHERE client_id=?',[$client]);
        $db->query('INSERT INTO client_station_favorites(client_id,station_id,sort_order) VALUES(?,?,0)',[$client,$c]);
        $db->query('UPDATE client_settings SET favorites_revision=favorites_revision+1 WHERE client_id=?',[$client]);
        $db->audit('replace','favorites',$client,['items'],2,3);
        // Real UNIQUE violation after a delete, an insert, revision and audit writes.
        $db->query('INSERT INTO client_station_favorites(client_id,station_id,sort_order) VALUES(?,?,0)',[$client,$a]);
    });
    throw new LogicException('Injected SQL violation accepted.');
}catch(PDOException $error){if((int)$error->errorInfo[1]!==1062)throw $error;}
if($lists->readFavorites($client)!==$before || (new MediaCentre\Client\SettingsRepository($db))->read($client)['favorites_revision']!==2 || (int)$pdo->query('SELECT COUNT(*) FROM audit_events')->fetchColumn()!==$auditBefore)throw new RuntimeException('Partial list/revision/audit survived rollback.');
$root=dirname(__DIR__,2);$dir=$root.'/.local/h2/concurrency';if(!is_dir($dir))mkdir($dir,0700,true);
$name=$uuid();$path=$dir.'/'.$name;
$pdo->beginTransaction();$db->lockSettings($client,1);
$process=proc_open([PHP_BINARY,__DIR__.'/h2-concurrent-worker.php',$client,$name],[1=>['pipe','w'],2=>['pipe','w']],$pipes,$root);
if(!is_resource($process))throw new RuntimeException('Cannot start transaction worker.');
try{
    $deadline=microtime(true)+10;
    while(!is_file($path.'.ready') && microtime(true)<$deadline)usleep(20000);
    if(!is_file($path.'.ready'))throw new RuntimeException('Concurrent worker not ready.');
    usleep(100000);
    if(is_file($path.'.result') || !proc_get_status($process)['running'])throw new RuntimeException('Concurrent update did not wait for lock.');
    $db->query("UPDATE client_settings SET preferences=JSON_OBJECT('radio_volume',55),revision=revision+1 WHERE client_id=?",[$client]);
    $pdo->commit();
    $stdout=stream_get_contents($pipes[1]);$stderr=stream_get_contents($pipes[2]);fclose($pipes[1]);fclose($pipes[2]);
    proc_close($process);
    if($stdout!=='' || $stderr!=='' || !is_file($path.'.result') || file_get_contents($path.'.result')!=='revision-conflict')throw new RuntimeException('Stale concurrent writer not rejected.');
}finally{if($pdo->inTransaction())$pdo->rollBack();}
$after=(new MediaCentre\Client\SettingsRepository($db))->read($client);
if($after['revision']!==2 || json_decode($after['preferences'],true)['radio_volume']!==55)throw new RuntimeException('Lost update occurred.');
$migrator=new MediaCentre\Storage\Migrator($pdo,$root.'/server/private/migrations');
if($migrator->migrate()!==[])throw new RuntimeException('Migration replay not empty.');
$temp=$root.'/.local/h2/migration-check';if(!is_dir($temp))mkdir($temp,0700,true);
file_put_contents($temp.'/001_base.php',(string)file_get_contents($root.'/server/private/migrations/001_base.php')."\n// modified checksum\n");
try{(new MediaCentre\Storage\Migrator($pdo,$temp))->migrate();throw new LogicException('Changed migration accepted.');}catch(RuntimeException){}
$pdo->exec("UPDATE schema_migrations SET state='applying'");
try{try{$migrator->migrate();throw new LogicException('Incomplete migration accepted.');}catch(RuntimeException){}}
finally{$pdo->exec("UPDATE schema_migrations SET state='applied'");}
if((int)$pdo->query('SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()')->fetchColumn()!==26)throw new RuntimeException('Unexpected schema after climate/music migrations.');
echo "PASS: real concurrent lock/revision conflict; rollback after delete/insert/revision/audit; UNIQUE and migration checksum/partial-state guards.\n";
