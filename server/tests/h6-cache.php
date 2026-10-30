<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
use MediaCentre\Storage\Database;
use MediaCentre\Shared\Uuid;
$db=new Database($pdo);$location=Uuid::random();$client=Uuid::random();$source=Uuid::random();
$locations=new MediaCentre\Weather\LocationRepository($db);
$locations->create($location,'Cache fixture','59.938630','30.314130','Europe/Moscow');
(new MediaCentre\Client\ClientRepository($db))->create($client,'H6 cache test',$location);
$db->query('INSERT IGNORE INTO app_state(id,server_id) VALUES(1,?)',[Uuid::random()]);
$token=bin2hex(random_bytes(32));$db->query('UPDATE clients SET token_hash=?,scopes=? WHERE id=?',[hash('sha256',$token,true),Database::json(MediaCentre\Security\ClientAccess::SCOPES),$client]);
$api=new MediaCentre\Http\Api($config,dirname(__DIR__));$headers=['authorization'=>'Bearer '.$token];
$get=static function(string $path,?string $etag=null)use($api,$headers):array{return $api->handle('GET','/api/v1/'.$path,[],$headers+($etag===null?[]:['if-none-match'=>$etag]),'');};
$assert=static fn(bool $ok,string $why)=>$ok?:throw new RuntimeException($why);
$fixture=json_decode((string)file_get_contents(dirname(__DIR__,2).'/contracts/v1/fixtures/success.json'),true,64,JSON_THROW_ON_ERROR);
(new MediaCentre\Weather\SnapshotRepository($db))->save($location,1,'synthetic',$fixture['weather']['data'],'2026-09-27T07:45:00Z','2026-09-27T08:00:00Z','2099-09-27T08:15:00Z',true);
$weather=$get('weather');$original=$db->one('SELECT * FROM weather_snapshots WHERE location_id=?',[$location]);
for($n=0;$n<3;$n++)$assert($get('weather',$weather['headers']['ETag'])['status']===304,'Unchanged snapshot did not validate');
$assert($db->one('SELECT * FROM weather_snapshots WHERE location_id=?',[$location])===$original,'Conditional reads extended or rewrote snapshot');
$boot=$get('bootstrap');$assert($boot['body']['data']['settings']['location']['revision']===1,'Location revision absent');
// Keep name/id/timezone unchanged: coordinates alone must invalidate the client weather cache.
$locations->update($location,1,'Cache fixture','59.950000','30.320000','Europe/Moscow');
$changed=$get('bootstrap',$boot['headers']['ETag']);$assert($changed['status']===200&&$changed['body']['data']['settings']['location']['revision']===2,'Coordinate edit invisible to cached bootstrap');
$assert($get('weather')['body']['meta']['freshness']==='empty','Previous-location forecast survived edit');
$db->query("INSERT INTO integrations(id,type,status,settings) VALUES(?,'cbr','ready',JSON_OBJECT())",[$source]);
$instruments=new MediaCentre\Finance\InstrumentRepository($db);$ids=[];
foreach(['USD','EUR'] as $symbol){$id=Uuid::random();$ids[]=$id;$instruments->create($id,['integration_id'=>$source,'type'=>'currency','symbol'=>$symbol,'exchange'=>'','board'=>'','quote_currency'=>'RUB','name'=>$symbol]);}
$lists=new MediaCentre\Client\OrderedListRepository($db);$lists->watchlist($client,1,array_map(fn($id)=>['id'=>$id,'show_on_dashboard'=>true],$ids));
$before=$get('finance');$boot=$get('bootstrap');
$lists->watchlist($client,2,[['id'=>$ids[1],'show_on_dashboard'=>false]]);
$after=$get('finance',$before['headers']['ETag']);$currentBoot=$get('bootstrap',$boot['headers']['ETag']);
$assert($after['status']===200&&array_column($after['body']['data']['items'],'id')===[$ids[1]],'Removed instrument stayed in finance representation');
$assert($currentBoot['status']===200&&$currentBoot['body']['data']['finance_list_revision']===3,'List revision not visible before dependent fetch');
$assert($get('dashboard')['body']['data']['finance']['data']['items']===[],'Hidden dashboard item remained visible');
echo "PASS H6 cache: real MySQL GET/304 never rewrite source dates; coordinate-only edits advance bootstrap location revision; removed/hidden instruments and their ETags update without providers.\n";
