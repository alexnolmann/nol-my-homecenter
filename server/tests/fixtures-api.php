<?php
declare(strict_types=1);
require dirname(__DIR__) . '/vendor/autoload.php';
use MediaCentre\Http\Api;
use MediaCentre\Shared\AppConfig;
use MediaCentre\Shared\ContractValidator;
use MediaCentre\Shared\FixtureRepository;
$root=dirname(__DIR__);
$validator=new ContractValidator(dirname($root) . '/contracts/v1/schema.json');
$repository=new FixtureRepository(dirname($root) . '/contracts/v1/fixtures');
$values=require $root . '/private/config/development.php';
$values['development']['token']=str_repeat('a',64);
$api=new Api(new AppConfig($values),$root);
$headers=['authorization'=>'Bearer '.str_repeat('a',64)];
$assert=static function(bool $condition,string $message): void {if (!$condition) throw new RuntimeException($message);};
foreach (['success','empty','partial','stale'] as $scenario) {
    $fixture=$repository->load($scenario);
    foreach (['bootstrap'=>'bootstrap','weather'=>'weather','finance'=>'finance','preferences'=>'preferencesResponse'] as $key=>$definition) $assert($validator->valid($definition,json_decode(json_encode($fixture[$key],JSON_THROW_ON_ERROR))), "$scenario $key schema failed");
    foreach (['bootstrap','dashboard','weather','finance','radio/stations','client/preferences'] as $path) {
        $response=$api->handle('GET','/api/v1/'.$path,['scenario'=>$scenario],$headers,'');
        $assert($response['status']===200,"$scenario $path failed");
        $assert($response['body']['meta']['test_data']===true,'Test data label missing');
        $cached=$api->handle('GET','/api/v1/'.$path,['scenario'=>$scenario],$headers+['if-none-match'=>$response['headers']['ETag']],'');
        $assert($cached['status']===304 && $cached['body']===null,'304 must not replace data or timestamps');
    }
    $assert($fixture['bootstrap']['data']['features']['home']===false,'Home enabled');
    foreach ($fixture['finance']['data']['items'] as $item) {
        $assert($item['price']===null || is_string($item['price']),'Price is not exact string');
        if ($item['trade_at']!==null) $assert($item['trade_at']!==$item['fetched_at'],'Trade time overwritten by fetch time');
    }
    $days=$fixture['weather']['data']['days'];
    if ($scenario==='empty') $assert($days===[] && $fixture['finance']['data']['items']===[],'Empty scenario not empty');
    else $assert(count($days)===7,'Seven-day weather missing');
}
$partial=$repository->load('partial');
$assert($partial['weather']['data']['current']['temperature_c']===null && $partial['finance']['data']['items'][4]['price']===null,'NULL scenarios missing');
$success=$repository->load('success');
$assert($success['finance']['data']['items'][5]['price']==='9876.543210987','Precision changed');
$assert(isset($success['bootstrap']['data']['future_bootstrap_field']),'Unknown field scenario missing');
$request=static fn(string $method,string $path,array $query=[],string $body='',array $extra=[]): array => $api->handle($method,$path,($query+['scenario'=>'success']),$extra+$headers,$body);
$assert($request('GET','/api/v1/weather',['days'=>'14'])['status']===200,'Fourteen-day weather request rejected');
$assert($request('GET','/api/v1/weather',['days'=>'13'])['status']===400,'Unsupported weather horizon accepted');
$assert($request('POST','/api/v1/weather/refresh')['status']===501,'Fixture refresh accepted');
$assert($api->handle('GET','/api/v1/bootstrap',[],[],'')['status']===401,'Anonymous access accepted');
$assert($request('GET','/api/v1/home/devices')['status']===404,'Home route active');
$assert($request('POST','/api/v1/finance')['headers']['Allow']==='GET','Allow missing');
foreach ([['limit'=>'0'],['limit'=>'51'],['offset'=>'-1'],['q'=>['x']],['favorites'=>'true'],['q'=>str_repeat('я',101)]] as $query) $assert($request('GET','/api/v1/radio/stations',$query)['status']===400,'Invalid search accepted');
$search=$request('GET','/api/v1/radio/stations',['q'=>'джаз']);
$assert($search['body']['data']['total']===1,'Unicode search failed');
$station=$search['body']['data']['items'][0];
$assert($request('GET','/api/v1/radio/stations/'.$station['id'])['status']===200,'Station detail missing');
$assert($request('PUT','/api/v1/radio/favorites/'.$station['id'])['status']===501,'Unpersisted write acknowledged');
$patch='{"revision":1,"radio_volume":55}';
$assert($request('PATCH','/api/v1/client/preferences',[],$patch)['status']===428,'Missing If-Match accepted');
$assert($request('PATCH','/api/v1/client/preferences',[],$patch,['if-match'=>'"wrong"'])['status']===412,'Stale If-Match accepted');
$etag=$request('GET','/api/v1/client/preferences')['headers']['ETag'];
$assert($request('PATCH','/api/v1/client/preferences',[],$patch,['if-match'=>$etag])['status']===501,'Unpersisted preferences acknowledged');
$assert($request('POST','/api/v1/client/heartbeat',[],'{"client_version":"h1","uptime_seconds":10,"current_screen":"home"}')['status']===200,'Heartbeat failed');
$assert($request('POST','/api/v1/client/heartbeat',[],'{"client_version":"0.9.0","uptime_seconds":10,"current_screen":"climate"}')['status']===200,'Local climate heartbeat rejected');
$assert($request('POST','/api/v1/client/heartbeat',[],'{"bad":1}')['status']===400,'Invalid heartbeat accepted');
$assert($request('POST','/api/v1/client/heartbeat',[],'{')['status']===400,'Malformed JSON accepted');
$production=new Api(new AppConfig(require $root.'/private/config/production.php'),$root);
$assert($production->handle('GET','/api/v1/bootstrap',[],$headers,'')['status']===503,'Production fixture leak');
echo "PASS: 4 shared scenarios; all GET routes/ETag; precision/dates/NULL; search; auth; write conflicts; production isolation.\n";
