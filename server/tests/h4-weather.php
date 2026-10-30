<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
use MediaCentre\Weather\{WeatherProvider,WeatherCollector,LocationRepository,SnapshotRepository};
use MediaCentre\Providers\{Transport,HttpResult,ProviderError};
use MediaCentre\Storage\Database;
$raw=json_decode(file_get_contents(dirname(__DIR__,2).'/contracts/providers/h4/open-meteo.json'),true,64,JSON_THROW_ON_ERROR);
$location=['id'=>MediaCentre\Shared\Uuid::random(),'name'=>'DST test','latitude'=>'52.500000','longitude'=>'13.400000','timezone'=>'Europe/Berlin','revision'=>1];$fetched='2026-03-27T23:18:00Z';
$value=WeatherProvider::normalize($raw,$location,$fetched);
if($value['payload']['days'][6]['date']!=='2026-04-03' || $value['payload']['current']['temperature_c']!=='-2.3' || $value['payload']['days'][2]['temp_min_c']!==null || $value['payload']['days'][6]['condition']!=='unknown' || $value['data_as_of']!=='2026-03-27T23:15:00Z')throw new RuntimeException('Weather units, dates or missing values lost.');
$extended=$raw;
foreach(range(7,13) as $i){foreach(['time','weather_code','temperature_2m_min','temperature_2m_max','precipitation_sum'] as $key)$extended['daily'][$key][]=$key==='time'?$raw['daily']['time'][0]+$i*86400:$raw['daily'][$key][$i%7];}
$extended['hourly_units']=['time'=>'unixtime','temperature_2m'=>'°C'];
for($i=0;$i<336;$i++){$extended['hourly']['time'][]=$raw['daily']['time'][0]+$i*3600;$extended['hourly']['temperature_2m'][]=5.5;$extended['hourly']['weather_code'][]=2;}
$long=WeatherProvider::normalize($extended,$location,$fetched)['payload'];
if(count($long['days'])!==14 || count($long['periods'])!==6 || $long['periods'][0]['temperature_c']!=='5.5')throw new RuntimeException('Long forecast or local periods missing.');
$invalid=$extended;array_pop($invalid['hourly']['weather_code']);try{WeatherProvider::normalize($invalid,$location,$fetched);throw new LogicException('Misaligned hourly values accepted.');}catch(InvalidArgumentException){}
$west=$raw;$west['timezone']='America/New_York';$west['utc_offset_seconds']=-14400;$west['daily']['time']=array_map(fn($i)=>strtotime('2026-03-27T00:00:00Z')+$i*86400+14400,range(0,6));
$westLocation=array_replace($location,['timezone'=>'America/New_York']);
if(WeatherProvider::normalize($west,$westLocation,$fetched)['payload']['days'][0]['date']!=='2026-03-27')throw new RuntimeException('Negative offset lost local day.');
foreach(['unit','alignment','future','number','date','code'] as $case){
    $bad=$raw;
    match($case){'unit'=>$bad['daily_units']['precipitation_sum']='inch','alignment'=>array_pop($bad['daily']['temperature_2m_min']),'future'=>$bad['current']['time']+=3600,'number'=>$bad['current']['temperature_2m']='-2.3','date'=>$bad['daily']['time'][1]+=(86400),'code'=>$bad['current']['weather_code']='3'};
    try{WeatherProvider::normalize($bad,$location,$fetched);throw new LogicException('Malformed weather accepted: '.$case);}catch(InvalidArgumentException){}
}
$db=new Database($pdo);(new LocationRepository($db))->create($location['id'],$location['name'],$location['latitude'],$location['longitude'],$location['timezone']);
$http=new class($raw,$fetched,$db) implements Transport {
    public bool $fail=false;public int $requests=0;
    public function __construct(private array $raw,private string $fetched,private Database $db){}
    public function get(string $url):HttpResult{if($this->db->pdo->inTransaction())throw new LogicException('Network called in transaction.');if(!str_contains($url,'forecast_days=14') || !str_contains($url,'hourly='))throw new LogicException('Extended forecast not requested.');$this->requests++;if($this->fail)throw new ProviderError('TIMEOUT');return new HttpResult(json_encode($this->raw,JSON_THROW_ON_ERROR),$this->fetched);}
};
$collector=new WeatherCollector($db,$http);if(!$collector->collect($location))throw new RuntimeException('Snapshot not stored.');
$before=$db->one('SELECT * FROM weather_snapshots WHERE location_id=?',[$location['id']]);$http->fail=true;
try{$collector->collect($location);throw new LogicException('Failure hidden.');}catch(ProviderError){}
$after=$db->one('SELECT * FROM weather_snapshots WHERE location_id=?',[$location['id']]);
foreach(['payload','data_as_of','fetched_at','valid_until'] as $key)if($before[$key]!==$after[$key])throw new RuntimeException('Failure changed successful snapshot.');
if($after['last_error_code']!=='TIMEOUT')throw new RuntimeException('Weather failure status absent.');
(new LocationRepository($db))->update($location['id'],1,'Changed','52.500000','13.400000','Europe/Berlin');$http->fail=false;
if($collector->collect($location) || $db->one('SELECT * FROM weather_snapshots WHERE location_id=?',[$location['id']]))throw new RuntimeException('Old location result accepted.');
foreach(['http://api.open-meteo.com/v1/forecast','https://example.invalid/audio.mp3','https://iss.moex.com/../../audio','https://www.cbr.ru/scripts/XML_daily.asp#secret'] as $url){try{(new MediaCentre\Providers\CurlTransport())->get($url);throw new LogicException('Forbidden endpoint fetched.');}catch(InvalidArgumentException){}}
echo "PASS H4 weather: units, 14 days/hourly periods, local dates/DST/negative offset, NULL, malformed data, source time, revision invalidation, failure preservation and network outside SQL.\n";
