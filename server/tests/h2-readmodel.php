<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
$db=new MediaCentre\Storage\Database($pdo);$client=MediaCentre\Shared\Uuid::random();$location=MediaCentre\Shared\Uuid::random();
(new MediaCentre\Weather\LocationRepository($db))->create($location,'Санкт-Петербург','59.938630','30.314130','Europe/Moscow');
(new MediaCentre\Client\ClientRepository($db))->create($client,'read test',$location);
$db->query('INSERT IGNORE INTO app_state(id,server_id) VALUES(1,?)',[MediaCentre\Shared\Uuid::random()]);
$values=$config->values;$values['development']['client_id']=$client;
$token=bin2hex(random_bytes(32));
$db->query('UPDATE clients SET token_hash=?,scopes=? WHERE id=?',[hash('sha256',$token,true),MediaCentre\Storage\Database::json(MediaCentre\Security\ClientAccess::SCOPES),$client]);
$api=new MediaCentre\Http\Api(new MediaCentre\Shared\AppConfig($values),dirname(__DIR__));
$headers=['authorization'=>'Bearer '.$token];
foreach(['bootstrap','dashboard','weather','finance','radio/stations','client/preferences'] as $path){
    $response=$api->handle('GET','/api/v1/'.$path,[],$headers,'');
    if($response['status']!==200)throw new RuntimeException('Stored read failed: '.$path);
}
$response=$api->handle('GET','/api/v1/weather',[],$headers,'');
if($response['body']['meta']['fetched_at']!==null || $response['body']['data']['current']['temperature_c']!==null)throw new RuntimeException('Empty data fabricated a timestamp/value.');
$etag=$response['headers']['ETag'];
if($api->handle('GET','/api/v1/weather',[],$headers+['if-none-match'=>$etag],'')['status']!==304)throw new RuntimeException('Stored ETag failed.');
$fixture=json_decode((string)file_get_contents(dirname(__DIR__,2).'/contracts/v1/fixtures/success.json'),true,512,JSON_THROW_ON_ERROR);
(new MediaCentre\Weather\SnapshotRepository($db))->save($location,1,'synthetic',$fixture['weather']['data'],'2026-09-27T07:45:00Z','2026-09-27T08:00:00Z','2026-09-27T08:15:00Z',true);
$response=$api->handle('GET','/api/v1/weather',[],$headers,'');
if($response['body']['meta']['fetched_at']!=='2026-09-27T08:00:00Z' || !$response['body']['meta']['test_data'])throw new RuntimeException('Original snapshot metadata lost.');
try{
    (new MediaCentre\Weather\SnapshotRepository($db))->save($location,1,'synthetic',['current'=>['temperature_c'=>14.5,'condition'=>'cloudy'],'days'=>[]],'2026-09-27T07:45:00Z','2026-09-27T08:01:00Z','2026-09-27T08:15:00Z',true);
    throw new LogicException('Numeric weather value accepted.');
}catch(InvalidArgumentException){}
if($api->handle('GET','/api/v1/weather',[],$headers,'')['body']['meta']['fetched_at']!=='2026-09-27T08:00:00Z')throw new RuntimeException('Invalid weather payload replaced original snapshot.');
$stations=new MediaCentre\Radio\StationRepository($db);$a=MediaCentre\Shared\Uuid::random();$z=MediaCentre\Shared\Uuid::random();
$stations->create($a,'A','https://example.invalid/a.mp3',[],'RU');$stations->create($z,'Z','https://example.invalid/z.mp3',[],'RU');
(new MediaCentre\Client\OrderedListRepository($db))->favorites($client,1,[$z,$a]);
$dashboard=$api->handle('GET','/api/v1/dashboard',[],$headers,'')['body'];
if(array_column($dashboard['data']['radio']['favorites'],'id')!==[$z,$a])throw new RuntimeException('Dashboard lost configured favorite order.');
echo "PASS: all stored API reads, empty nullable timestamps, ETag and original snapshot dates.\n";
