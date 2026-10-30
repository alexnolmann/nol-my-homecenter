<?php
declare(strict_types=1);
require dirname(__DIR__).'/private/bootstrap.php';
use MediaCentre\Admin\Pages;
$assert=static fn(bool $ok,string $why)=>$ok ?: throw new RuntimeException($why);
$now=strtotime('2026-10-03T12:00:00Z');
foreach([[null,'Нет данных'],['bad','Нет данных'],['2026-10-03 12:00:01','Дата в будущем · проверьте часы'],['2026-10-03 11:59:35.000','25 с назад'],['2026-10-03 11:58:00','2 мин назад'],['2026-10-03 10:00:00','2 ч назад'],['2026-10-01 12:00:00','2 дн назад']] as [$value,$expected])$assert(Pages::age($value,$now)===$expected,'UTC age label incorrect');
$hostile='"><img src=x onerror=alert(1)><script>alert(2)</script>';
$id='10000000-0000-4000-8000-000000000001';$source='10000000-0000-4000-8000-000000000002';
$fixture=[
    'locations'=>[['id'=>$id,'revision'=>1,'name'=>$hostile,'latitude'=>'59.9','longitude'=>'30.3','timezone'=>'Europe/Moscow']],
    'stations'=>[['id'=>$id,'revision'=>1,'name'=>$hostile,'stream_url'=>'https://example.invalid/?x=%22','genres'=>json_encode([$hostile]),'country_code'=>'RU','enabled'=>true]],
    'station_page'=>['query'=>$hostile,'number'=>1,'pages'=>1,'total'=>1],
    'weather_provider'=>['provider'=>'open_meteo','revision'=>1,'has_visual_crossing_key'=>false],
    'instruments'=>[['id'=>$id,'revision'=>1,'integration_id'=>$source,'name'=>$hostile,'type'=>'share','symbol'=>'TEST','board'=>'TQBR','enabled'=>true]],
    'clients'=>[['id'=>$id,'name'=>$hostile,'revision'=>1,'finance_list_revision'=>1,'auth_revision'=>1,'location_id'=>$id,'last_seen_at'=>'2026-10-03 11:58:00','app_version'=>$hostile,'paired'=>true,'revoked_at'=>null,'scopes'=>'[]']],
    'watchlists'=>[],
    'collections'=>[['target_key'=>'weather:'.$id,'kind'=>'weather','label'=>$hostile,'board'=>null,'current_configuration'=>true,'last_result'=>'failed','job_status'=>'queued','last_error_code'=>'RATE_LIMITED','last_attempt_at'=>'2026-10-03 11:59:00','last_success_at'=>null,'weather_as_of'=>'2026-10-03 10:00:00','weather_fetched'=>'2026-10-03 10:01:00','quote_fetched'=>null,'quote_as_of'=>null,'effective_date'=>null,'next_due_at'=>'2026-10-03 12:05:00','failure_count'=>3]],
];
$render=static function(string $page,array $data,string $hostile):string{
    $section=$page;$view=$data;$admin=['login'=>$hostile];$message=$hostile;$csrf=str_repeat('a',64);$e=Pages::escape(...);
    ob_start();try{require dirname(__DIR__).'/private/views/admin/panel.php';return ob_get_contents();}finally{ob_end_clean();}
};
foreach(['overview','weather','radio','finance','clients','system'] as $section){
    $html=$render($section,$fixture,$hostile);$assert(!str_contains($html,$hostile) && str_contains($html,'&lt;img'),'Editable string not escaped');
    $dom=new DOMDocument();$prior=libxml_use_internal_errors(true);try{$dom->loadHTML('<?xml encoding="UTF-8">'.$html);}finally{libxml_clear_errors();libxml_use_internal_errors($prior);}
    $xpath=new DOMXPath($dom);$assert($xpath->query('//img|//*[@onerror or @onload]|//script[not(@src="/assets/admin.js")]')->length===0,'Hostile value created active markup');
    if($section==='overview')foreach(['Связь с планшетами','2026-10-03 11:58:00','Давность получения','RATE_LIMITED','Повторов: 3','2026-10-03 12:05:00'] as $expected)$assert(str_contains($html,$expected),'Overview missing heartbeat/age/error/backoff');
}
echo "PASS H6 admin: UTC snapshot/heartbeat age including missing/future dates; overview heartbeat/error/backoff; hostile names, genres, search, version and notices remain inert in six DOM-parsed sections.\n";
