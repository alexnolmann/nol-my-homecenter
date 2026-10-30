<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
$config=require dirname(__DIR__,2).'/server/private/bootstrap.php';
if(!$config->isDevelopment())throw new RuntimeException('H2 initializer is development-only.');
try{
    $db=new MediaCentre\Storage\Database(MediaCentre\Storage\Connection::open($config));
    $client=$config->values['development']['client_id'];
    if(!(new MediaCentre\Client\ClientRepository($db))->find($client)){
        $location='20000000-0000-4000-8000-000000000001';
        $locations=new MediaCentre\Weather\LocationRepository($db);
        if(!$locations->find($location))$locations->create($location,'Санкт-Петербург','59.938630','30.314130','Europe/Moscow');
        (new MediaCentre\Client\ClientRepository($db))->create($client,'ASUS K014',$location);
    }
    $db->query('INSERT IGNORE INTO app_state(id,server_id) VALUES(1,?)',[MediaCentre\Shared\Uuid::random()]);
    echo "Development panel initialized; no fabricated weather/quotes or audio streams inserted.\n";
}catch(Throwable){echo "Initialization failed; credentials and SQL diagnostics suppressed.\n";exit(1);}
