<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
$db=new MediaCentre\Storage\Database($pdo);
$id=MediaCentre\Shared\Uuid::random();$client=MediaCentre\Shared\Uuid::random();
$locations=new MediaCentre\Weather\LocationRepository($db);$clients=new MediaCentre\Client\ClientRepository($db);$settings=new MediaCentre\Client\SettingsRepository($db);
$locations->create($id,"Тест ' OR 1=1 --",'59.938630','30.314130','Europe/Moscow');
$clients->create($client,'repository test',$id);
if($locations->find($id)['name']!=="Тест ' OR 1=1 --")throw new RuntimeException('Prepared value corrupted.');
if($settings->update($client,1,['radio_volume'=>55])!==2 || $settings->read($client)['revision']!==2)throw new RuntimeException('Revision update failed.');
try{$settings->update($client,1,['radio_volume'=>66]);throw new RuntimeException('Stale revision accepted.');}catch(MediaCentre\Storage\RevisionConflict){}
try{$db->transaction(function()use($db,$client):void{$db->query('UPDATE client_settings SET revision=revision+1 WHERE client_id=?',[$client]);throw new LogicException('injected failure');});}catch(LogicException){}
if($settings->read($client)['revision']!==2)throw new RuntimeException('Transaction was not rolled back.');
$bad=MediaCentre\Shared\Uuid::random();try{$clients->create($bad,'must rollback',MediaCentre\Shared\Uuid::random());throw new RuntimeException('Foreign key accepted.');}catch(PDOException){}
if($clients->find($bad)!==null)throw new RuntimeException('Partial client creation persisted.');
echo "PASS: prepared PDO values, revision conflict, rollback and atomic FK failure on isolated MySQL.\n";
