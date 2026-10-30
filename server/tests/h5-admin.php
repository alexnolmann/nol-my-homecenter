<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
$db=new MediaCentre\Storage\Database($pdo);$forms=new MediaCentre\Admin\FormActions($db);$assert=fn(bool $ok,string $why)=>$ok ?: throw new RuntimeException($why);$prefix='H5WEB_'.bin2hex(random_bytes(6));
$bad=['action'=>'station','id'=>'','name'=>'','stream_url'=>'https://secret:password@example.invalid/audio#fragment','genres'=>'Jazz, Jazz','country_code'=>'R'];
try{$forms->save($bad);throw new RuntimeException('Invalid fields saved');}catch(MediaCentre\Admin\FieldErrors $errors){$fields=$errors->fields;}
$assert(array_keys($fields)===['name','stream_url','genres','country_code'],'Not all editable errors identified');
$assert(!$db->one('SELECT id FROM radio_stations WHERE stream_url=?',[$bad['stream_url']]),'Invalid write persisted');
$render=static function(array $view,string $section,array $invalidForm=[],array $fieldErrors=[]):string{$admin=['login'=>'test'];$message='Проверьте поля';$csrf='isolated-placeholder';$e=MediaCentre\Admin\Pages::escape(...);ob_start();require dirname(__DIR__).'/private/views/admin/panel.php';return (string)ob_get_clean();};
$html=$render($forms->view(['query'=>'','page'=>1]),'radio',$bad,$fields);
$assert(substr_count($html,'aria-invalid="true"')===4 && str_contains($html,'Жанры') && str_contains($html,'Jazz, Jazz'),'Inline errors or new-form values missing');
$assert(!str_contains($html,'secret:password') && !str_contains($html,'value="password"'),'Invalid URL credentials echoed into HTML');
$stations=[];
for($n=0;$n<23;$n++){$name=$prefix.' '.sprintf('%02d',$n);$forms->save(['action'=>'station','id'=>'','name'=>$name,'stream_url'=>'https://example.invalid/original','genres'=>'Jazz','country_code'=>'RU','enabled'=>'1']);$stations[]=$db->one('SELECT id FROM radio_stations WHERE name=?',[$name])['id'];}
$first=$forms->view(['query'=>$prefix,'page'=>1]);$second=$forms->view(['query'=>$prefix,'page'=>2]);$assert(count($first['stations'])===20 && count($second['stations'])===3 && $first['station_page']['total']===23,'Bounded web pages failed');
$assert(array_merge(array_column($first['stations'],'id'),array_column($second['stations'],'id'))===$stations,'Stable web order changed');
$literal=$prefix.' %_!';$forms->save(['action'=>'station','id'=>'','name'=>$literal,'stream_url'=>'https://example.invalid/original','genres'=>'Blues','country_code'=>'RU']);$exact=$forms->view(['query'=>$literal,'page'=>1]);$assert(count($exact['stations'])===1 && !$exact['stations'][0]['enabled'],'Literal wildcard search or create-disabled failed');
$assert($forms->view(['query'=>$prefix.' missing','page'=>999])['station_page']['number']===1,'Empty/out-of-range page not corrected');
foreach([['query'=>[],'page'=>1],['query'=>str_repeat('x',101),'page'=>1],['query'=>'','page'=>0]] as $badQuery){try{$forms->view($badQuery);throw new RuntimeException('Invalid search accepted');}catch(InvalidArgumentException){}}
$location=MediaCentre\Shared\Uuid::random();$client=MediaCentre\Shared\Uuid::random();(new MediaCentre\Weather\LocationRepository($db))->create($location,$prefix,'59.938630','30.314130','Europe/Moscow');(new MediaCentre\Client\ClientRepository($db))->create($client,$prefix,$location);
$source=MediaCentre\Shared\Uuid::random();(new MediaCentre\Integration\IntegrationRepository($db))->create($source,'moex_iss',['data_mode'=>'public_delayed']);
$disabledSymbol='H5_OFF_'.strtoupper(bin2hex(random_bytes(4)));
$forms->save(['action'=>'instrument','id'=>'','type'=>'share','symbol'=>$disabledSymbol,'board'=>'TQBR','name'=>$prefix.' disabled']);
$disabled=$db->one('SELECT enabled,revision FROM finance_instruments WHERE symbol=?',[$disabledSymbol]);
$assert($disabled!==null && !$disabled['enabled'] && (int)$disabled['revision']===1,'Unchecked new instrument was enabled or required a second write');
try{$forms->save(['action'=>'instrument','id'=>'','type'=>'unsupported']);throw new RuntimeException('Invalid type saved');}catch(MediaCentre\Admin\FieldErrors $error){$assert(isset($error->fields['type']),'Invalid type has no field error');}
try{MediaCentre\Admin\FormModel::instrument(['integration_id'=>$source,'type'=>'share','symbol'=>'!','board'=>'','exchange'=>'MOEX','quote_currency'=>'RUB','name'=>'']);throw new RuntimeException('Invalid instrument saved');}catch(MediaCentre\Admin\FieldErrors $error){$assert(array_keys($error->fields)===['name','symbol','board'],'Instrument errors not aggregated');}
$repo=new MediaCentre\Finance\InstrumentRepository($db);$shares=[];
foreach(['A','B'] as $suffix){$id=MediaCentre\Shared\Uuid::random();$repo->create($id,['integration_id'=>$source,'type'=>'share','symbol'=>'H5_'.$suffix.'_'.strtoupper(bin2hex(random_bytes(4))),'exchange'=>'MOEX','board'=>'TQBR','quote_currency'=>'RUB','name'=>$prefix.' '.$suffix]);$shares[]=$id;}
$form=['action'=>'watchlist','client_id'=>$client,'revision'=>'1','items'=>[$shares[0]=>['included'=>'1','order'=>'1'],$shares[1]=>['included'=>'1','order'=>'0','dashboard'=>'1']]];$forms->save($form);
$list=(new MediaCentre\Client\OrderedListRepository($db))->readWatchlist($client);$assert(array_column($list,'instrument_id')===array_reverse($shares) && (bool)$list[0]['show_on_dashboard'],'Ordered save lost order/dashboard flag');
$form['revision']='2';$form['items'][$shares[0]]['order']='0';try{$forms->save($form);throw new RuntimeException('Duplicate order saved');}catch(MediaCentre\Admin\FieldErrors $error){$assert(isset($error->fields['items['.$shares[1].'][order]']),'Order field not identified');}
$assert((new MediaCentre\Client\OrderedListRepository($db))->readWatchlist($client)===$list,'Invalid order changed saved list');
(new MediaCentre\Jobs\CollectionQueue($db))->sync(MediaCentre\Jobs\CollectionPlanner::describe($db));
$db->query("UPDATE collection_targets SET last_result='failed',last_error_code='INSTRUMENT_UNAVAILABLE',last_attempt_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 MINUTE),next_due_at=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 HOUR) WHERE target_key=?",['moex:'.$shares[0]]);
$view=$forms->view();$html=$render($view,'finance');
$assert(str_contains($html,'Фоновая проверка') && str_contains($html,'INSTRUMENT_UNAVAILABLE') && str_contains($html,'Запросить проверку в фоне'),'Background result/queue control missing');
$assert(str_contains($html,'data-move="-1"') && str_contains($html,'data-included') && str_contains($html,'aria-label="На главной '),'Accessible ordering controls missing');
$forms->save(['action'=>'refresh','target_key'=>'moex:'.$shares[0]]);$row=$db->one('SELECT next_due_at FROM collection_targets WHERE target_key=?',['moex:'.$shares[0]]);$assert($row['next_due_at']<gmdate('Y-m-d H:i:s',time()+10),'Refresh did not queue future work');
$assert($db->one('SELECT last_result FROM collection_targets WHERE target_key=?',['moex:'.$shares[0]])['last_result']==='failed','POST pretended that a provider check already succeeded');
$db->query("UPDATE collection_targets SET last_attempt_at=UTC_TIMESTAMP(3),next_due_at=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 HOUR) WHERE target_key=?",['moex:'.$shares[0]]);$throttled=$db->one('SELECT next_due_at FROM collection_targets WHERE target_key=?',['moex:'.$shares[0]])['next_due_at'];$forms->save(['action'=>'refresh','target_key'=>'moex:'.$shares[0]]);$assert($db->one('SELECT next_due_at FROM collection_targets WHERE target_key=?',['moex:'.$shares[0]])['next_due_at']===$throttled,'Manual request bypassed cooldown');
echo "PASS: field-specific errors/value retention/redaction, bounded literal catalogue search, stable pages, disabled creation, ordered saves with rollback, accessible controls and background result/refresh without providers.\n";
