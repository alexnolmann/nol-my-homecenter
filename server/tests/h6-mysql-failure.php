<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
$root=dirname(__DIR__,2);$mode=$argv[1]??'';$file=$root.'/.local/h6/mysql/fixture.json';
$base=require $root.'/server/private/bootstrap.php';$credentials=json_decode((string)file_get_contents($root.'/.local/h2/mysql/credentials.json'),true,512,JSON_THROW_ON_ERROR);
$values=$base->values;$values['database']=['host'=>'127.0.0.1','port'=>$credentials['port'],'name'=>'mediacentre_h2_test','user'=>'mc_h2','password'=>$credentials['application_password']];
$config=new MediaCentre\Shared\AppConfig($values);
use MediaCentre\Storage\{Connection,Database};
use MediaCentre\Shared\Uuid;
if($mode==='prepare'){
    if(is_file($file))throw new RuntimeException('Previous private failure fixture must be cleaned first.');
    $db=new Database(Connection::open($config));$state=['client'=>Uuid::random(),'location'=>Uuid::random(),'token'=>bin2hex(random_bytes(32))];
    if(!is_dir(dirname($file)))mkdir(dirname($file),0700,true);
    file_put_contents($file,json_encode($state,JSON_THROW_ON_ERROR));
    (new MediaCentre\Weather\LocationRepository($db))->create($state['location'],'H6 DB failure','59.938630','30.314130','Europe/Moscow');
    (new MediaCentre\Client\ClientRepository($db))->create($state['client'],'H6 DB failure',$state['location']);
    $db->query('UPDATE clients SET token_hash=?,scopes=? WHERE id=?',[hash('sha256',$state['token'],true),Database::json(MediaCentre\Security\ClientAccess::SCOPES),$state['client']]);
    $fixture=json_decode((string)file_get_contents($root.'/contracts/v1/fixtures/success.json'),true,64,JSON_THROW_ON_ERROR);
    (new MediaCentre\Weather\SnapshotRepository($db))->save($state['location'],1,'synthetic',$fixture['weather']['data'],'2026-09-27T07:45:00Z','2026-09-27T08:00:00Z','2099-09-27T08:15:00Z',true);
}else{
    if(!is_file($file))throw new RuntimeException('Prepared failure fixture absent.');
    $state=json_decode((string)file_get_contents($file),true,512,JSON_THROW_ON_ERROR);
}
if($mode==='cleanup'){
    $db=new Database(Connection::open($config));$db->transaction(function()use($db,$state):void{
        $db->query('DELETE FROM client_settings WHERE client_id=?',[$state['client']]);
        $db->query('DELETE FROM clients WHERE id=?',[$state['client']]);
        $db->query('DELETE FROM weather_snapshots WHERE location_id=?',[$state['location']]);
        $db->query('DELETE FROM locations WHERE id=?',[$state['location']]);
    });unlink($file);echo "Private H6 MySQL fixture removed.\n";exit;
}
if(!in_array($mode,['prepare','down','up'],true))throw new InvalidArgumentException('Unknown failure phase.');
$api=new MediaCentre\Http\Api($config,$root.'/server');$start=microtime(true);
$response=$api->handle('GET','/api/v1/weather',[],['authorization'=>'Bearer '.$state['token']],'');
$expected=$mode==='down'?503:200;
if($response['status']!==$expected)throw new RuntimeException('Wrong database-failure response.');
$body=Database::json($response['body']);
foreach([$state['token'],$credentials['application_password'],'PDOException','SQLSTATE','mysql:host='] as $private)if(str_contains($body,$private))throw new RuntimeException('Database failure leaked internal details.');
if($mode==='prepare'){$state['body_hash']=hash('sha256',Database::json($response['body']['data']));$state['fetched_at']=$response['body']['meta']['fetched_at'];file_put_contents($file,json_encode($state,JSON_THROW_ON_ERROR));}
if($mode==='up'&&(hash('sha256',Database::json($response['body']['data']))!==$state['body_hash']||$response['body']['meta']['fetched_at']!==$state['fetched_at']))throw new RuntimeException('Restart changed snapshot or source time.');
if(microtime(true)-$start>12)throw new RuntimeException('Database failure exceeded bounded connection time.');
echo "PASS H6 MySQL $mode: status=$expected; bounded request, safe error, original credentials/snapshot preserved.\n";
