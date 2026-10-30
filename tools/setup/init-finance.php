<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
$root=dirname(__DIR__,2);$config=require $root.'/server/private/bootstrap.php';
if(!$config->isDevelopment())throw new RuntimeException('H2 development initialization only.');
try{
    $db=new MediaCentre\Storage\Database(MediaCentre\Storage\Connection::open($config));
    $client=$config->values['development']['client_id'];$lists=new MediaCentre\Client\OrderedListRepository($db);
    if($lists->readWatchlist($client)){echo "Existing configured watchlist retained.\n";exit;}
    $settings=(new MediaCentre\Client\SettingsRepository($db))->read($client);
    if(!$settings)throw new RuntimeException('Initialize panel first.');
    $sources=[];
    foreach(['cbr'=>'official','moex_iss'=>'public_delayed'] as $type=>$mode){
        $row=$db->one('SELECT id FROM integrations WHERE type=? ORDER BY created_at,id LIMIT 1',[$type]);
        $id=$row['id'] ?? MediaCentre\Shared\Uuid::random();
        if(!$row)(new MediaCentre\Integration\IntegrationRepository($db))->create($id,$type,['data_mode'=>$mode]);
        $sources[$type]=$id;
    }
    $items=[];$instruments=new MediaCentre\Finance\InstrumentRepository($db);
    foreach(['USD','EUR','CNY','SBER','GAZP','LKOH'] as $symbol){
        $currency=in_array($symbol,['USD','EUR','CNY'],true);
        $fields=['integration_id'=>$sources[$currency?'cbr':'moex_iss'],'type'=>$currency?'currency':'share','symbol'=>$symbol,'exchange'=>$currency?'':'MOEX','board'=>$currency?'':'TQBR','quote_currency'=>'RUB','name'=>$symbol];
        $identity=MediaCentre\Finance\InstrumentRepository::identity($fields);
        $row=$db->one('SELECT id FROM finance_instruments WHERE instrument_key=?',[$identity['instrument_key']]);
        $id=$row['id'] ?? MediaCentre\Shared\Uuid::random();
        if(!$row)$instruments->create($id,$fields);
        $items[]=['id'=>$id,'show_on_dashboard'=>$currency];
    }
    $lists->watchlist($client,(int)$settings['finance_list_revision'],$items);
    echo "Configured USD/EUR/CNY and SBER/GAZP/LKOH; no quotes/weather synthesized.\n";
}catch(Throwable){echo "Configuration initialization failed; credentials/SQL diagnostics suppressed.\n";exit(1);}
