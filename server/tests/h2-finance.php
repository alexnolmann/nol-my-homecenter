<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
$db=new MediaCentre\Storage\Database($pdo);$uuid=fn()=>MediaCentre\Shared\Uuid::random();$source=$uuid();$id=$uuid();
$db->query("INSERT INTO integrations(id,type,status,settings) VALUES(?,'moex_iss','ready',JSON_OBJECT())",[$source]);
$repo=new MediaCentre\Finance\InstrumentRepository($db);
$fields=['integration_id'=>$source,'type'=>'share','symbol'=>'lkoh','exchange'=>'moex','board'=>'tqbr','quote_currency'=>'rub','name'=>'Лукойл'];
$repo->create($id,$fields);
try{$repo->create($uuid(),$fields);throw new RuntimeException('Duplicate identity accepted.');}catch(PDOException){}
$quotes=new MediaCentre\Finance\QuoteRepository($db);
$q=['price'=>'9876.543210987','nominal'=>'1','previous_price'=>'9870','comparison_basis'=>'previous_close','reference_date'=>'2026-09-25','quote_as_of'=>'2026-09-27T07:30:00Z','data_as_of'=>'2026-09-27T07:30:00Z','fetched_at'=>'2026-09-27T08:00:00Z','valid_until'=>'2026-09-27T08:15:00Z','delay_seconds'=>null,'test_data'=>true];
$quotes->save($id,1,$q);$row=$quotes->find($id);
if(MediaCentre\Finance\Decimal::canonical($row['price'])!==$q['price'] || $row['quote_as_of']===$row['fetched_at'] || $row['delay_seconds']!==null || $row['previous_price']===null)throw new RuntimeException('Price/date/delay corruption.');
$q['reference_date']='2026-09-28';$quotes->save($id,1,$q);
if($quotes->find($id)['previous_price']!==null || $quotes->find($id)['change_pct']!==null)throw new RuntimeException('Incomparable reference accepted.');
$fields['board']='TQTF';$repo->update($id,1,$fields);
if($quotes->find($id)!==null || $quotes->save($id,1,$q)!==false)throw new RuntimeException('Old board price/job survived.');
foreach([1.2,'1e3','1.1234567890123'] as $bad){try{MediaCentre\Finance\Decimal::value($bad);throw new RuntimeException('Invalid decimal accepted.');}catch(InvalidArgumentException){}}
$cbr=$uuid();$currency=$uuid();
$db->query("INSERT INTO integrations(id,type,status,settings) VALUES(?,'cbr','ready',JSON_OBJECT())",[$cbr]);
$currencyFields=['integration_id'=>$cbr,'type'=>'currency','symbol'=>'usd','exchange'=>'','board'=>'','quote_currency'=>'RUB','name'=>'USD'];
$repo->create($currency,$currencyFields);
try{
    $db->query("INSERT INTO finance_instruments(id,instrument_key,integration_id,type,symbol,exchange,board,name,quote_currency) VALUES(?,?,?,'currency','USD','','','USD','RUB')",[$uuid(),$uuid(),$cbr]);
    throw new LogicException('Composite identity duplicate accepted.');
}catch(PDOException $error){if((int)$error->errorInfo[1]!==1062)throw $error;}
try{
    $db->query("INSERT INTO finance_instruments(id,instrument_key,integration_id,type,symbol,exchange,board,name,quote_currency) VALUES(?,?,?,'currency','EUR','',NULL,'EUR','RUB')",[$uuid(),$uuid(),$cbr]);
    throw new LogicException('NULL identity key accepted.');
}catch(PDOException $error){if((int)$error->errorInfo[1]!==1048)throw $error;}
$rates=['price'=>'15.0003','nominal'=>'10','effective_date'=>'2026-09-27','previous_price'=>'14.5','comparison_basis'=>'previous_published_rate','reference_date'=>'2026-09-26','data_as_of'=>'2026-09-27T07:30:00Z','fetched_at'=>'2026-09-27T08:00:00Z','valid_until'=>'2026-09-27T08:15:00Z','test_data'=>true];
$quotes->save($currency,1,$rates);
if(MediaCentre\Finance\Decimal::canonical($quotes->find($currency)['nominal'])!=='10' || $quotes->find($currency)['effective_date']!=='2026-09-27')throw new RuntimeException('Source nominal/rate date lost.');
$rates['price']=null;$rates['fetched_at']='2026-09-27T08:01:00Z';$quotes->save($currency,1,$rates);
if(MediaCentre\Finance\Decimal::canonical($quotes->find($currency)['price'])!=='15.0003' || $quotes->find($currency)['fetched_at']!=='2026-09-27 08:00:00.000')throw new RuntimeException('Partial source error erased/rejuvenated last price.');
$night=$uuid();$nightFields=$fields;$nightFields['symbol']='SBER';$nightFields['board']='TQBR';$repo->create($night,$nightFields);
$quotes->save($night,1,['price'=>'123.45','nominal'=>'1','previous_price'=>'120','comparison_basis'=>'previous_close','reference_date'=>'2026-09-26','quote_as_of'=>'2026-09-26T23:30:00Z','fetched_at'=>'2026-09-27T00:00:00Z','valid_until'=>'2026-09-27T00:15:00Z','source_timezone'=>'Europe/Moscow','test_data'=>true]);
if($quotes->find($night)['previous_price']===null)throw new RuntimeException('Comparison lost local trading date at UTC midnight.');
echo "PASS: unique normalized identity, exact DECIMAL, original dates, unknown delay, comparable reference and stale-job invalidation.\n";
