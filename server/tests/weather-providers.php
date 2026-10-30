<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
use MediaCentre\Weather\{MetNorwayProvider,VisualCrossingProvider,WeatherSettingsRepository};
use MediaCentre\Storage\Database;
$location=['latitude'=>'59.938630','longitude'=>'30.314130','timezone'=>'Europe/Moscow'];
$fetched='2026-10-05T12:00:00Z';$start=strtotime('2026-10-04T21:00:00Z');$series=[];
for($i=0;$i<40;$i++)$series[]=['time'=>gmdate('Y-m-d\TH:i:s\Z',$start+$i*21600),'data'=>['instant'=>['details'=>['air_temperature'=>5.5,'relative_humidity'=>70,'wind_speed'=>2.5]],'next_1_hours'=>['summary'=>['symbol_code'=>'lightrain'],'details'=>['precipitation_amount'=>0.2]]]];
$met=['properties'=>['meta'=>['updated_at'=>'2026-10-05T11:00:00Z','units'=>['air_temperature'=>'celsius','relative_humidity'=>'%','wind_speed'=>'m/s','precipitation_amount'=>'mm']],'timeseries'=>$series]];
$result=MetNorwayProvider::normalize($met,$location,$fetched,'2026-10-05T13:00:00Z');
if(count($result['payload']['days'])<7||count($result['payload']['periods'])!==6||$result['payload']['current']['condition']!=='rain'||$result['valid_until']!=='2026-10-05T13:00:00Z')throw new RuntimeException('MET normalization failed.');
$bad=$met;$bad['properties']['meta']['units']['wind_speed']='km/h';try{MetNorwayProvider::normalize($bad,$location,$fetched);throw new RuntimeException('Bad MET units accepted.');}catch(InvalidArgumentException){}
$days=[];for($i=0;$i<8;$i++){$date=gmdate('Y-m-d',strtotime('2026-10-05T00:00:00Z')+$i*86400);$hours=[];for($j=0;$j<24;$j++)$hours[]=['datetimeEpoch'=>strtotime($date.'T'.sprintf('%02d',$j).':00:00+03:00'),'temp'=>10.0,'icon'=>'rain'];$days[]=['datetime'=>$date,'tempmin'=>4.0,'tempmax'=>12.0,'precip'=>1.0,'icon'=>'rain','hours'=>$hours];}
$vc=['timezone'=>'Europe/Moscow','currentConditions'=>['datetimeEpoch'=>strtotime('2026-10-05T14:00:00+03:00'),'temp'=>10.0,'feelslike'=>9.0,'humidity'=>80.0,'windspeed'=>18.0,'icon'=>'rain'],'days'=>$days];
$result=VisualCrossingProvider::normalize($vc,$location,$fetched);$vcResult=$result;
if(count($result['payload']['days'])!==8||count($result['payload']['periods'])!==6||$result['payload']['current']['wind_speed_ms']!=='5')throw new RuntimeException('Visual Crossing normalization failed.');
$db=new Database($pdo);$settings=new WeatherSettingsRepository($db);$old=$settings->state();$apiKey='TestKey_12345';$forms=new MediaCentre\Admin\FormActions($db);
try{
    $forms->save(['action'=>'weather_provider','provider'=>'visual_crossing','revision'=>(string)$old['revision'],'visual_crossing_key'=>$apiKey]);
    $revision=$settings->state()['revision'];
    if($settings->key()!==$apiKey||$settings->state()['provider']!=='visual_crossing')throw new RuntimeException('Visual Crossing key not saved.');
    $view=$forms->view();$admin=['login'=>'test'];$section='weather';$message='';$csrf='test';$e=MediaCentre\Admin\Pages::escape(...);ob_start();require dirname(__DIR__).'/private/views/admin/panel.php';$html=ob_get_clean();
    if(str_contains($html,$apiKey)||!str_contains($html,'Visual Crossing')||!str_contains($html,'Сохранён'))throw new RuntimeException('Weather panel leaked or hid provider credentials.');
    $row=$db->one('SELECT credentials_ciphertext,nonce,key_id FROM weather_provider_settings WHERE id=1');
    if(str_contains($row['credentials_ciphertext'],$apiKey)||strlen($row['nonce'])!==24)throw new RuntimeException('Weather key is not encrypted.');
    $locationId=MediaCentre\Shared\Uuid::random();$clientId=MediaCentre\Shared\Uuid::random();
    (new MediaCentre\Weather\LocationRepository($db))->create($locationId,'Provider switch','59.938630','30.314130','Europe/Moscow');
    (new MediaCentre\Client\ClientRepository($db))->create($clientId,'provider-test',$locationId);
    $plans=MediaCentre\Jobs\CollectionPlanner::describe($db);$planned=null;foreach($plans as $target)if($target['key']==='weather:'.$locationId)$planned=$target;
    if($planned===null||$planned['context']['provider']!=='visual_crossing'||$planned['context']['provider_revision']!==$revision||str_contains(Database::json($planned),$apiKey))throw new RuntimeException('Provider plan leaked key or lost revision.');
    $revision=$settings->save('met_norway',$revision,null,false);
    if((new MediaCentre\Weather\SnapshotRepository($db))->save($locationId,1,'visual_crossing',$vcResult['payload'],$vcResult['data_as_of'],$vcResult['fetched_at'],$vcResult['valid_until'],false,$planned['context']['provider_revision']))throw new RuntimeException('In-flight previous provider result accepted.');
    if($settings->key()!==$apiKey)throw new RuntimeException('Weather key lost when switching.');
    $revision=$settings->save('open_meteo',$revision,null,true);
    if($settings->key()!==null)throw new RuntimeException('Weather key not removed.');
}finally{
    $state=$settings->state();if($state['provider']!=='open_meteo'||$state['has_visual_crossing_key'])$settings->save('open_meteo',$state['revision'],null,true);
}
echo "PASS weather providers: MET and Visual Crossing normalization, unit rejection, provider switching, encrypted API key lifecycle.\n";
