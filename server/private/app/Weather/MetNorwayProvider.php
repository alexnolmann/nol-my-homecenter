<?php
declare(strict_types=1);
namespace MediaCentre\Weather;
use MediaCentre\Providers\{Transport,ProviderError};
final class MetNorwayProvider
{
    public function __construct(private Transport $http){}
    public function fetch(array $location): array
    {
        $url='https://api.met.no/weatherapi/locationforecast/2.0/compact?'.http_build_query(['lat'=>round((float)$location['latitude'],4),'lon'=>round((float)$location['longitude'],4)]);
        $response=$this->http->get($url);
        try{return self::normalize(json_decode($response->body,true,64,JSON_THROW_ON_ERROR),$location,$response->fetchedAt,$response->expiresAt);}
        catch(\InvalidArgumentException|\JsonException|\TypeError|\ValueError){throw new ProviderError('INVALID_RESPONSE');}
    }
    public static function normalize(array $raw,array $location,string $fetched,?string $expires=null): array
    {
        $now=WeatherValues::timestamp($fetched);$zone=new \DateTimeZone($location['timezone']);
        $units=$raw['properties']['meta']['units'] ?? null;$series=$raw['properties']['timeseries'] ?? null;
        if(!is_array($units)||!is_array($series)||!array_is_list($series)||count($series)<24||count($series)>400)throw new \InvalidArgumentException('Invalid MET forecast.');
        foreach(['air_temperature'=>'celsius','relative_humidity'=>'%','wind_speed'=>'m/s','precipitation_amount'=>'mm'] as $field=>$unit)if(($units[$field] ?? null)!==$unit)throw new \InvalidArgumentException('MET units changed.');
        $updated=WeatherValues::timestamp($raw['properties']['meta']['updated_at'] ?? null);
        if($updated>$now+300||$updated<$now-86400)throw new \InvalidArgumentException('Invalid MET update time.');
        $daily=[];$periods=[];$previous=null;$current=null;$currentTime=null;
        foreach($series as $entry){
            if(!is_array($entry)||!is_array($entry['data']['instant']['details'] ?? null))throw new \InvalidArgumentException('Invalid MET forecast entry.');
            $at=WeatherValues::timestamp($entry['time'] ?? null);if($previous!==null&&$at<=$previous)throw new \InvalidArgumentException('MET timeline is unordered.');$previous=$at;
            $details=$entry['data']['instant']['details'];$temperature=WeatherValues::number($details['air_temperature'] ?? null,-100,70);
            $symbol=$entry['data']['next_1_hours']['summary']['symbol_code'] ?? $entry['data']['next_6_hours']['summary']['symbol_code'] ?? null;
            if($symbol!==null&&!is_string($symbol))throw new \InvalidArgumentException('Invalid MET symbol.');
            $condition=WeatherValues::condition($symbol);
            if($at<=$now&&($currentTime===null||$at>$currentTime)){$current=$entry;$currentTime=$at;}
            if($at>$now&&$current===null){$current=$entry;$currentTime=$at;}
            $date=(new \DateTimeImmutable('@'.$at))->setTimezone($zone)->format('Y-m-d');
            if(!isset($daily[$date]))$daily[$date]=['date'=>$date,'min'=>null,'max'=>null,'precip'=>0.0,'has_precip'=>false,'condition'=>'unknown'];
            if($temperature!==null){$temp=(float)$temperature;$daily[$date]['min']=$daily[$date]['min']===null?$temp:min($temp,$daily[$date]['min']);$daily[$date]['max']=$daily[$date]['max']===null?$temp:max($temp,$daily[$date]['max']);}
            $hour=(int)(new \DateTimeImmutable('@'.$at))->setTimezone($zone)->format('G');
            if($condition!=='unknown'&&($daily[$date]['condition']==='unknown'||$hour===12))$daily[$date]['condition']=$condition;
            $amount=$entry['data']['next_1_hours']['details']['precipitation_amount'] ?? $entry['data']['next_6_hours']['details']['precipitation_amount'] ?? null;
            if($amount!==null){$daily[$date]['precip']+=(float)WeatherValues::number($amount,0,3000);$daily[$date]['has_precip']=true;}
            if($at>$now&&count($periods)<6&&$hour%6===0)$periods[]=['at'=>WeatherValues::iso($at),'temperature_c'=>$temperature,'condition'=>$condition];
        }
        if($current===null||$currentTime===null||$currentTime<$now-21600||$currentTime>$now+21600)throw new \InvalidArgumentException('MET current weather unavailable.');
        $today=(new \DateTimeImmutable('@'.$now))->setTimezone($zone)->format('Y-m-d');$days=[];
        for($i=0;$i<14;$i++){
            $date=(new \DateTimeImmutable($today))->modify('+'.$i.' days')->format('Y-m-d');if(!isset($daily[$date]))break;
            $day=$daily[$date];$days[]=['date'=>$date,'temp_min_c'=>WeatherValues::number($day['min'],-100,70),'temp_max_c'=>WeatherValues::number($day['max'],-100,70),'precipitation_mm'=>$day['has_precip']?WeatherValues::number($day['precip'],0,3000):null,'condition'=>$day['condition'],'weather_code'=>null];
        }
        if(count($days)<7)throw new \InvalidArgumentException('MET forecast too short.');
        $details=$current['data']['instant']['details'];$symbol=$current['data']['next_1_hours']['summary']['symbol_code'] ?? $current['data']['next_6_hours']['summary']['symbol_code'] ?? null;
        $valid=$expires===null?$now+1800:WeatherValues::timestamp($expires);$valid=max($now,min($now+86400,$valid));
        return ['payload'=>['current'=>['temperature_c'=>WeatherValues::number($details['air_temperature'] ?? null,-100,70),'condition'=>WeatherValues::condition(is_string($symbol)?$symbol:null),'weather_code'=>null,'apparent_temperature_c'=>null,'humidity_percent'=>WeatherValues::number($details['relative_humidity'] ?? null,0,100),'wind_speed_ms'=>WeatherValues::number($details['wind_speed'] ?? null,0,200),'source_time'=>WeatherValues::iso($currentTime)],'days'=>$days,'periods'=>$periods,'attribution'=>'MET Norway · CC BY 4.0'],'data_as_of'=>WeatherValues::iso($updated),'fetched_at'=>$fetched,'valid_until'=>WeatherValues::iso($valid)];
    }
}
