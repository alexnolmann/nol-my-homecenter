<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
use MediaCentre\Admin\FormModel;
$bad=[
 fn()=>FormModel::location(['name'=>'city','latitude'=>59.9,'longitude'=>'30','timezone'=>'Europe/Moscow']),
 fn()=>FormModel::location(['name'=>'city','latitude'=>'91','longitude'=>'30','timezone'=>'Europe/Moscow']),
 fn()=>FormModel::station(['name'=>'radio','stream_url'=>'https://user:pass@example.invalid/a','genres'=>[],'country_code'=>'RU']),
 fn()=>FormModel::preferences(['scopes'=>['home:control']]),
 fn()=>FormModel::preferences(['radio_volume'=>null]),
 fn()=>FormModel::rights(['scopes'=>['home:control'],'revision'=>1]),
 fn()=>FormModel::rights(['scopes'=>['radio:read'],'revision'=>'1']),
 fn()=>FormModel::station(['name'=>'radio','stream_url'=>'file:///etc/passwd','genres'=>[],'country_code'=>'RU']),
];
foreach($bad as $case){try{$case();throw new RuntimeException('Unsafe form accepted.');}catch(InvalidArgumentException){}}
$db=new MediaCentre\Storage\Database($pdo);$id=MediaCentre\Shared\Uuid::random();$location=MediaCentre\Shared\Uuid::random();
(new MediaCentre\Weather\LocationRepository($db))->create($location,'Санкт-Петербург','59.938630','30.314130','Europe/Moscow');
(new MediaCentre\Client\ClientRepository($db))->create($id,'rights test',$location);
$rights=new MediaCentre\Client\RightsRepository($db);$rights->change($id,1,['radio:read']);
try{$rights->change($id,1,[]);throw new RuntimeException('Stale rights revision accepted.');}catch(MediaCentre\Storage\RevisionConflict){}
if($db->one('SELECT revision FROM client_settings WHERE client_id=?',[$id])['revision']!==1)throw new RuntimeException('Rights changed preference revision.');
$station=MediaCentre\Shared\Uuid::random();
$stations=new MediaCentre\Radio\StationRepository($db);
$stations->create($station,' station ',' https://example.invalid/a ',[' Jazz '],'RU');
if($stations->find($station)['stream_url']!=='https://example.invalid/a' || $stations->find($station)['name']!=='station')throw new RuntimeException('Validated normalized form values not persisted.');
$source=MediaCentre\Shared\Uuid::random();$instrument=MediaCentre\Shared\Uuid::random();
(new MediaCentre\Integration\IntegrationRepository($db))->create($source,'moex_iss');
$form=FormModel::instrument(['integration_id'=>$source,'type'=>'share','symbol'=>'sber','exchange'=>'moex','board'=>'tqbr','quote_currency'=>'rub','name'=>' Сбер ']);
(new MediaCentre\Finance\InstrumentRepository($db))->create($instrument,$form->values);
echo "PASS: form models, strict fields/URLs/coordinates, isolated scopes and rights revision.\n";
