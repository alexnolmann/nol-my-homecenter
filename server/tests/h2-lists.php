<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
$db=new MediaCentre\Storage\Database($pdo);
$uuid=fn()=>MediaCentre\Shared\Uuid::random();
$location=$uuid();$client=$uuid();$a=$uuid();$b=$uuid();
$locations=new MediaCentre\Weather\LocationRepository($db);
$locations->create($location,'Санкт-Петербург','59.938630','30.314130','Europe/Moscow');
(new MediaCentre\Client\ClientRepository($db))->create($client,'list test',$location);
$stations=new MediaCentre\Radio\StationRepository($db);
$stations->create($a,'A','https://example.invalid/a.mp3',['Jazz'],'RU');
$stations->create($b,'B','http://example.invalid/b.mp3',['Ambient'],'RU');
$lists=new MediaCentre\Client\OrderedListRepository($db);
$lists->favorites($client,1,[$b,$a]);
$stations->update($b,1,'B','http://example.invalid/b.mp3',['Ambient'],'RU',false);
if(array_column($lists->readFavorites($client),'station_id')!==[$b,$a])throw new RuntimeException('Disable lost favorites/order.');
try{$lists->favorites($client,2,[$a,$uuid()]);throw new RuntimeException('Unknown item accepted.');}catch(InvalidArgumentException){}
if(array_column($lists->readFavorites($client),'station_id')!==[$b,$a])throw new RuntimeException('Invalid replacement lost list.');
try{$lists->favorites($client,1,[$a]);throw new RuntimeException('Stale list accepted.');}catch(MediaCentre\Storage\RevisionConflict){}
try{$lists->favorites($client,2,[$a,$a]);throw new RuntimeException('Duplicate accepted.');}catch(InvalidArgumentException){}
$integration=$uuid();$one=$uuid();$two=$uuid();
$db->query("INSERT INTO integrations(id,type,status,settings) VALUES(?,'moex_iss','ready',JSON_OBJECT())",[$integration]);
foreach([[$one,'SBER'],[$two,'GAZP']] as [$id,$symbol])$db->query("INSERT INTO finance_instruments(id,instrument_key,integration_id,type,symbol,exchange,board,name,quote_currency) VALUES(?,?,?,'share',?,'MOEX','TQBR',?,'RUB')",[$id,$integration.':'.$symbol,$integration,$symbol,$symbol]);
$lists->watchlist($client,1,[['id'=>$two,'show_on_dashboard'=>true],['id'=>$one,'show_on_dashboard'=>false]]);
$lists->watchlist($client,2,[['id'=>$one,'show_on_dashboard'=>true],['id'=>$two,'show_on_dashboard'=>false]]);
$settings=(new MediaCentre\Client\SettingsRepository($db))->read($client);
if(array_column($lists->readWatchlist($client),'instrument_id')!==[$one,$two] || $settings['finance_list_revision']!==3 || $settings['revision']!==1 || $settings['favorites_revision']!==2)throw new RuntimeException('List revisions/order are not independent.');
$oversized=array_map(fn()=> $uuid(),range(1,51));
try{$lists->favorites($client,2,$oversized);throw new LogicException('More than 50 favorites accepted.');}catch(InvalidArgumentException){}
try{$lists->watchlist($client,3,array_map(fn($id)=>['id'=>$id,'show_on_dashboard'=>true],$oversized));throw new LogicException('More than 50 instruments accepted.');}catch(InvalidArgumentException){}
if(array_column($lists->readFavorites($client),'station_id')!==[$b,$a] || array_column($lists->readWatchlist($client),'instrument_id')!==[$one,$two] || (new MediaCentre\Client\SettingsRepository($db))->read($client)!==$settings)throw new RuntimeException('Oversized replacement changed saved lists/revisions.');
$locations->update($location,1,'Санкт-Петербург','59.938631','30.314130','Europe/Moscow');
echo "PASS: durable location/catalogue, disabled favorites, ordered lists, separate revisions and invalid replacements.\n";
