<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
use MediaCentre\Storage\Database;
use MediaCentre\Shared\Uuid;
use MediaCentre\Finance\{InstrumentRepository,QuoteRepository,MoexQuotesProvider};
use MediaCentre\Weather\SnapshotRepository;
use MediaCentre\Integration\IntegrationRepository;
use MediaCentre\Jobs\{CollectionQueue,CollectionWorker};
use MediaCentre\Providers\{Transport,HttpResult};
$db=new Database($pdo);$location=Uuid::random();$client=Uuid::random();$source=Uuid::random();$instrument=Uuid::random();
(new MediaCentre\Weather\LocationRepository($db))->create($location,'Snapshot test','59.938630','30.314130','Europe/Moscow');
(new MediaCentre\Client\ClientRepository($db))->create($client,'snapshot reader',$location);
$db->query('INSERT IGNORE INTO app_state(id,server_id) VALUES(1,?)',[Uuid::random()]);
$token=bin2hex(random_bytes(32));$db->query('UPDATE clients SET token_hash=?,scopes=? WHERE id=?',[hash('sha256',$token,true),Database::json(MediaCentre\Security\ClientAccess::SCOPES),$client]);
$api=new MediaCentre\Http\Api($config,dirname(__DIR__));$headers=['authorization'=>'Bearer '.$token];
$get=static function(string $path)use($api,$headers):array{$response=$api->handle('GET','/api/v1/'.$path,[],$headers,'');if($response['status']!==200)throw new RuntimeException('Snapshot API read failed.');return $response;};
$weather=new SnapshotRepository($db);$weather->failure($location,1,'TIMEOUT',gmdate('Y-m-d\TH:i:s\Z'));
if($get('weather')['body']['meta']['freshness']!=='empty' || $get('weather')['body']['meta']['fetched_at']!==null)throw new RuntimeException('First failed weather attempt fabricated an old snapshot.');
$fixture=json_decode(file_get_contents(dirname(__DIR__,2).'/contracts/v1/fixtures/success.json'),true,512,JSON_THROW_ON_ERROR);
$payload=$fixture['weather']['data'];unset($payload['location']);
$weather->save($location,1,'synthetic',$payload,'2026-09-27T07:45:00Z','2026-09-27T08:00:00Z','2026-09-27T08:15:00Z',true);
if($weather->save($location,1,'synthetic',$payload,'2026-09-27T07:30:00Z','2026-09-27T08:01:00Z','2026-09-27T08:16:00Z',true))throw new RuntimeException('Later HTTP response rolled weather source time backwards.');
$integrations=new IntegrationRepository($db);$integrations->create($source,'moex_iss',['data_mode'=>'public_delayed']);$instruments=new InstrumentRepository($db);
$fields=['integration_id'=>$source,'type'=>'share','symbol'=>'SNAPSHOT','exchange'=>'MOEX','board'=>'TQBR','quote_currency'=>'RUB','name'=>'Snapshot'];$instruments->create($instrument,$fields);
(new MediaCentre\Client\OrderedListRepository($db))->watchlist($client,1,[['id'=>$instrument,'show_on_dashboard'=>true]]);
$security=['SECID'=>'SNAPSHOT','BOARDID'=>'TQBR','CURRENCYID'=>'SUR','PREVLEGALCLOSEPRICE'=>'120','PREVDATE'=>'2026-09-25'];
$market=['SECID'=>'SNAPSHOT','BOARDID'=>'TQBR','LAST'=>'123.456789012345','TIME'=>'18:59:54','UPDATETIME'=>'19:00:00','SYSTIME'=>'2026-09-26 19:00:17','NUMTRADES'=>'5','TRADINGSTATUS'=>'N'];$version=['trade_date'=>'2026-09-26','trade_session_date'=>'2026-09-28'];
$q=MoexQuotesProvider::normalize($security,$market,$version,'2026-09-27T11:00:00Z');$quotes=new QuoteRepository($db);$quotes->save($instrument,1,$q);
$older=$q;$older['data_as_of']='2026-09-26T15:50:00Z';$older['fetched_at']='2026-09-27T11:01:00Z';
if($quotes->save($instrument,1,$older))throw new RuntimeException('Later HTTP response rolled quote source time backwards.');
$older=$q;$older['quote_as_of']='2026-09-26T15:50:00Z';$older['fetched_at']='2026-09-27T11:01:00Z';
if($quotes->save($instrument,1,$older))throw new RuntimeException('Last trade time rolled backwards.');
$quotes->failure($instrument,1,'TIMEOUT','2026-09-27T11:02:00Z');
$before=$quotes->find($instrument);$item=$get('finance')['body']['data']['items'][0];
if($item['price']!==$q['price'] || $item['status']!=='stale' || $item['fetched_at']!==$q['fetched_at'] || $item['trade_at']!==$q['quote_as_of'] || $item['access_mode']!=='anonymous_delayed' || $item['observed_delay_seconds']!==null || $item['price_field']!=='LAST')throw new RuntimeException('Snapshot dates, missing measured lag or stale usable price lost.');
$fingerprint=static function()use($db):string{return hash('sha256',serialize([$db->all('SELECT * FROM weather_snapshots ORDER BY location_id'),$db->all('SELECT * FROM finance_quotes ORDER BY instrument_id'),$db->all('SELECT * FROM jobs ORDER BY id'),$db->all('SELECT * FROM collection_targets ORDER BY target_key')]));};
$stable=$fingerprint();
for($n=0;$n<3;$n++)foreach(['dashboard','weather','finance'] as $path){$r=$get($path);if($api->handle('GET','/api/v1/'.$path,[],$headers+['if-none-match'=>$r['headers']['ETag']],'')['status']!==304)throw new RuntimeException('Snapshot ETag not stable.');}
if($fingerprint()!==$stable || $quotes->find($instrument)['fetched_at']!==$before['fetched_at'])throw new RuntimeException('Opening tabs changed snapshots or scheduled collection.');
// The test runner disables curl_init and URL fopen: these reads work without a provider transport.
if(function_exists('curl_init') || filter_var(ini_get('allow_url_fopen'),FILTER_VALIDATE_BOOL))throw new RuntimeException('Run this suite with provider network functions disabled.');
$queue=new CollectionQueue($db);$db->query('DELETE FROM collection_targets');$db->query("DELETE FROM jobs WHERE queue='collection'");
$table=static fn(array $row):array=>['columns'=>array_keys($row),'data'=>[array_values($row)]];
$raw=json_encode(['securities'=>$table($security),'marketdata'=>$table($market),'dataversion'=>$table($version)],JSON_THROW_ON_ERROR);
foreach(['board','mode','source'] as $change){
    $captured=$instruments->find($instrument);$queue->sync([['key'=>'moex:'.$instrument,'kind'=>'moex','id'=>$instrument,'context'=>$captured]]);$queue->enqueue();
    $edit=static function()use($change,$captured,$fields,$instruments,$integrations,$db,$source,$instrument):void{
        if($change==='mode'){$revision=(int)$db->one('SELECT revision FROM integrations WHERE id=?',[$source])['revision'];$integrations->update($source,$revision,'moex_iss',['data_mode'=>'unknown']);}
        else{$new=array_intersect_key($captured,$fields);if($change==='board')$new['board']='TQTF';else{$newSource=Uuid::random();$integrations->create($newSource,'moex_iss',['data_mode'=>'public_delayed']);$new['integration_id']=$newSource;}$instruments->update($instrument,(int)$captured['revision'],$new);}
    };
    $raw=json_encode(['securities'=>$table(array_replace($security,['BOARDID'=>$captured['board']])),'marketdata'=>$table(array_replace($market,['BOARDID'=>$captured['board']])),'dataversion'=>$table($version)],JSON_THROW_ON_ERROR);
    $http=new class($raw,$edit) implements Transport{public int $requests=0;public function __construct(private string $raw,private Closure $edit){}public function get(string $url):HttpResult{$this->requests++;($this->edit)();return new HttpResult($this->raw,'2026-09-27T11:03:00Z');}};
    if((new CollectionWorker($db,$http))->run(1)!==1 || $http->requests!==1 || $quotes->find($instrument)!==null)throw new RuntimeException('In-flight previous board/mode job restored removed price.');
    $empty=$get('finance')['body']['data']['items'][0];if($empty['price']!==null || $empty['status']!=='empty' || $empty['trade_at']!==null)throw new RuntimeException('Invalidated quote remained visible.');
}
echo "PASS H4 snapshots: transport-disabled GET/ETag, unchanged jobs/snapshots, first failure empty, retained original dates, source/trade chronology and in-flight board/access-mode fencing.\n";
