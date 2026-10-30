<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';$db=new MediaCentre\Storage\Database($pdo);
$admin=$db->one('SELECT * FROM admin_users WHERE login=?',['h3-test-admin']);$location=$db->one('SELECT id FROM locations ORDER BY created_at LIMIT 1');if(!$admin||!$location)throw new RuntimeException('Run H3 isolated suites first.');
$pair=new MediaCentre\Security\PairingRepository($db);$issued=$pair->issue($admin['id'],(int)$admin['auth_revision'],$location['id'],null,MediaCentre\Security\ClientAccess::SCOPES);
$api=new MediaCentre\Http\Api($config,dirname(__DIR__));$ip='h5-cat-'.bin2hex(random_bytes(8));$paired=$api->handle('POST','/api/v1/pair',[],[],MediaCentre\Storage\Database::json(['code'=>$issued['code'],'client_name'=>'H5 catalogue test','client_version'=>'h5']),$ip);
if($paired['status']!==200)throw new RuntimeException('Isolated pairing failed.');$client=$paired['body']['data'];$headers=['authorization'=>'Bearer '.$client['token'],'x-mediacentre-server-id'=>$client['server_id']];
$call=fn(array $query)=>$api->handle('GET','/api/v1/radio/stations',$query,$headers,'',$ip);$assert=fn($ok,$message)=>$ok ?: throw new RuntimeException($message);
$prefix='H5 Каталог '.bin2hex(random_bytes(4));$stations=[];$repo=new MediaCentre\Radio\StationRepository($db);
for($n=0;$n<13;$n++){$id=MediaCentre\Shared\Uuid::random();$name=$prefix.' '.sprintf('%02d',$n);$genres=$n===11?['0']:($n%2===0?['Jazz','Blues']:['Rock']);$repo->create($id,$name,'https://example.invalid/original/'.$n,$genres,'RU');$stations[]=['id'=>$id,'name'=>$name,'genres'=>$genres];}
$lists=new MediaCentre\Client\OrderedListRepository($db);$lists->favorite($client['client_id'],$stations[0]['id'],true);$lists->favorite($client['client_id'],$stations[12]['id'],true);
$repo->update($stations[12]['id'],1,$stations[12]['name'],'https://example.invalid/original/12',$stations[12]['genres'],'RU',false);
$seen=[];$validator=new MediaCentre\Shared\ContractValidator(dirname(__DIR__,2).'/contracts/v1/schema.json');
foreach([0,5,10] as $offset){$page=$call(['q'=>$prefix,'limit'=>'5','offset'=>(string)$offset]);$assert($page['status']===200 && $validator->valid('stations',json_decode(json_encode($page['body']))),'Catalogue page contract failed.');$data=$page['body']['data'];$assert($data['total']===13 && $data['offset']===$offset && count($data['items'])===min(5,13-$offset),'Pagination bounds/count failed.');foreach($data['items'] as $station)$seen[]=$station['id'];}
$assert($seen===array_column($stations,'id') && count(array_unique($seen))===13,'Stable pages skipped or duplicated stations.');
$jazz=$call(['q'=>$prefix,'genre'=>'Jazz'])['body']['data'];$assert($jazz['total']===7 && $jazz['items'][0]['genres']===['Jazz','Blues'],'Individual genre matched only joined label.');
$assert(in_array('Rock',$jazz['available_genres'],true) && in_array('Blues',$jazz['available_genres'],true),'Genre choices changed with active filtering.');
$assert(in_array('0',$jazz['available_genres'],true),'Numeric genre label was coerced to a JSON number or discarded.');
$favorite=$call(['q'=>$prefix,'genre'=>'Blues','favorites'=>'1'])['body']['data'];$assert($favorite['total']===2 && $favorite['items'][1]['enabled']===false,'Combined genre/favorites lost disabled favorite.');
$deleted=$api->handle('DELETE','/api/v1/radio/favorites/'.$stations[12]['id'],[],$headers,'',$ip);$assert($deleted['status']===200 && $call(['q'=>$prefix,'favorites'=>'1'])['body']['data']['total']===1,'Disabled favorite could not be removed.');
$assert($call(['q'=>str_replace('Каталог','каталог',$prefix)])['body']['data']['total']===13,'Unicode case-insensitive search failed.');
$empty=$call(['q'=>$prefix,'offset'=>'99'])['body']['data'];$assert($empty['items']===[] && $empty['total']===13,'Beyond-final-page result invalid.');
foreach([['limit'=>'0'],['limit'=>'51'],['offset'=>'-1'],['q'=>str_repeat('я',101)],['genre'=>str_repeat('я',61)]] as $bad)$assert($call($bad)['status']===400,'Unbounded query accepted.');
$disposable=MediaCentre\Shared\Uuid::random();$repo->create($disposable,'H5 disposable station','https://example.invalid/disposable',[],'RU');
$db->transaction(function()use($db,$disposable):void{$db->query('DELETE FROM radio_stations WHERE id=?',[$disposable]);$db->audit('delete','station',$disposable,[],1,2);});
$assert(!$repo->find($disposable) && $db->one("SELECT action FROM audit_events WHERE object_id=? AND action='delete'",[$disposable])!==null,'Owned demo cleanup deletion was not audited.');
echo "PASS: 13 stable paginated stations, Unicode search, multi-genre membership, independent choices, combined favorites and disabled removal; bounded query validation.\n";
