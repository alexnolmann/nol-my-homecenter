<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
$db=new MediaCentre\Storage\Database($pdo);$uuid=fn()=>MediaCentre\Shared\Uuid::random();
$assert=fn(bool $ok,string $why)=>$ok ?: throw new RuntimeException($why);
$location=$uuid();$client=$uuid();$cbr=$uuid();$moex=$uuid();
(new MediaCentre\Weather\LocationRepository($db))->create($location,'H5 finance','59.938630','30.314130','Europe/Moscow');
(new MediaCentre\Client\ClientRepository($db))->create($client,'H5 finance',$location);
$db->query('INSERT IGNORE INTO app_state(id,server_id) VALUES(1,?)',[$uuid()]);
foreach([$cbr=>'cbr',$moex=>'moex_iss'] as $id=>$type)$db->query("INSERT INTO integrations(id,type,status,settings) VALUES(?,?,'ready',JSON_OBJECT())",[$id,$type]);
$repo=new MediaCentre\Finance\InstrumentRepository($db);$quotes=new MediaCentre\Finance\QuoteRepository($db);$ids=[];$fields=[];
foreach(['USD','EUR','SBER','GAZP'] as $n=>$symbol){
    $id=$uuid();$currency=$n<2;$ids[]=$id;$fields[$id]=['integration_id'=>$currency?$cbr:$moex,'type'=>$currency?'currency':'share','symbol'=>$symbol,'exchange'=>$currency?'':'MOEX','board'=>$currency?'':'TQBR','quote_currency'=>'RUB','name'=>'H5 '.$symbol];$repo->create($id,$fields[$id]);
}
(new MediaCentre\Client\OrderedListRepository($db))->watchlist($client,1,array_map(fn($id)=>['id'=>$id,'show_on_dashboard'=>true],$ids));
$base=['price'=>'15.000300000001','nominal'=>'10','previous_price'=>'14.5','comparison_basis'=>'previous_published_rate','reference_date'=>'2026-09-25','effective_date'=>'2026-09-28','data_as_of'=>'2026-09-27T07:30:00Z','fetched_at'=>'2026-09-27T08:00:00Z','valid_until'=>'2099-09-27T08:15:00Z','test_data'=>true];
$quotes->save($ids[0],1,$base);
$share=array_replace($base,['price'=>'100.123456789012','nominal'=>'1','previous_price'=>null,'comparison_basis'=>'none','reference_date'=>null,'effective_date'=>null,'quote_as_of'=>'2026-09-25T15:45:00Z','data_as_of'=>'2026-09-25T15:45:00Z','delay_seconds'=>900,'market_state'=>'closed','payload'=>['access_mode'=>'anonymous_delayed','reference_error'=>'REFERENCE_UNAVAILABLE','price_field'=>'LAST']]);
$quotes->save($ids[2],1,$share);
$repo->update($ids[3],1,$fields[$ids[3]],false);
$read=new MediaCentre\Storage\ReadModel($db,true);$all=$read->load($client);$items=$all['finance']['data']['items'];
$assert(array_column($items,'id')===$ids,'List order changed');
$assert($items[0]['name']==='H5 USD' && $items[0]['enabled']===true && $items[0]['price']===$base['price'] && $items[0]['nominal']==='10','Name/enabled/decimal/nominal lost');
$assert($items[0]['rate_date']==='2026-09-28' && $items[0]['fetched_at']==='2026-09-27T08:00:00Z','Rate date replaced with fetch date');
$assert($items[1]['price']===null && $items[1]['rate_date']===null && $items[1]['change_percent']===null,'Absent quote invented values');
$assert($items[2]['status']==='partial' && $items[2]['price']===$share['price'] && $items[2]['reference_error']==='REFERENCE_UNAVAILABLE','Reference failure erased price');
$assert($items[2]['trade_at']==='2026-09-25T15:45:00Z' && $items[2]['fetched_at']==='2026-09-27T08:00:00Z' && $items[2]['delay_minutes']===15 && $items[2]['access_mode']==='anonymous_delayed' && $items[2]['market_state']==='closed','Share date/delay/market lost');
$assert($items[3]['enabled']===false && $items[3]['price']===null && $items[3]['trade_at']===null,'Disabled instrument displayed a price');
$assert($all['dashboard']['data']['finance']['data']['items']===$items,'Dashboard and financial data disagree');
$quotes->failure($ids[0],1,'PROVIDER_TIMEOUT','2026-09-27T09:00:00Z');$stale=$read->load($client)['finance']['data']['items'][0];
$assert($stale['status']==='stale' && $stale['price']===$base['price'] && $stale['fetched_at']===$base['fetched_at'] && $stale['last_error_code']==='PROVIDER_TIMEOUT','Failed refresh rejuvenated or erased quote');
echo "PASS: named/enabled financial rows, exact unit price, future effective rate, old closed trade, declared delay, partial reference, missing/disabled and failed refresh dates.\n";
