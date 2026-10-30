<?php
declare(strict_types=1);
namespace MediaCentre\Weather;
use MediaCentre\Providers\{Transport,ProviderError};
final class VisualCrossingProvider
{
    public function __construct(private Transport $http){}
    public function fetch(array $location,string $key): array
    {
        if(!preg_match('/^[A-Za-z0-9_-]{8,128}$/D',$key))throw new ProviderError('CREDENTIAL_MISSING');
        $url='https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/'.$location['latitude'].','.$location['longitude'].'?'.http_build_query(['unitGroup'=>'metric','include'=>'current,days,hours','key'=>$key]);
        $result=$this->http->get($url);
        try{return self::normalize(json_decode($result->body,true,64,JSON_THROW_ON_ERROR),$location,$result->fetchedAt);}
        catch(\InvalidArgumentException|\JsonException|\TypeError|\ValueError){throw new ProviderError('INVALID_RESPONSE');}
    }
    public static function normalize(array $raw,array $location,string $fetched): array
    {
        $now=WeatherValues::timestamp($fetched);
        if(($raw['timezone'] ?? null)!==$location['timezone'] || !is_array($raw['currentConditions'] ?? null) || !is_array($raw['days'] ?? null))throw new \InvalidArgumentException('Weather location mismatch.');
        $zone=new \DateTimeZone($location['timezone']);$current=$raw['currentConditions'];$observed=$current['datetimeEpoch'] ?? null;
        if(!is_int($observed)||$observed>$now+3600||$observed<$now-86400)throw new \InvalidArgumentException('Invalid current conditions time.');
        $days=[];$periods=[];$today=(new \DateTimeImmutable('@'.$observed))->setTimezone($zone)->format('Y-m-d');
        if(count($raw['days'])<7||count($raw['days'])>16)throw new \InvalidArgumentException('Invalid forecast length.');
        foreach(array_slice($raw['days'],0,14) as $index=>$day){
            if(!is_array($day)||($day['datetime'] ?? null)!==(new \DateTimeImmutable($today))->modify('+'.$index.' days')->format('Y-m-d'))throw new \InvalidArgumentException('Forecast dates not consecutive.');
            $minimum=WeatherValues::number($day['tempmin'] ?? null,-100,70);$maximum=WeatherValues::number($day['tempmax'] ?? null,-100,70);
            if($minimum!==null&&$maximum!==null&&bccomp($minimum,$maximum,3)>0)throw new \InvalidArgumentException('Inverted daily temperature.');
            $days[]=['date'=>$day['datetime'],'temp_min_c'=>$minimum,'temp_max_c'=>$maximum,'precipitation_mm'=>WeatherValues::number($day['precip'] ?? null,0,3000),'condition'=>WeatherValues::condition(is_string($day['icon'] ?? null)?$day['icon']:null),'weather_code'=>null];
            foreach(($day['hours'] ?? []) as $hour){
                if(!is_array($hour)||!is_int($hour['datetimeEpoch'] ?? null))throw new \InvalidArgumentException('Invalid hourly forecast.');
                $at=$hour['datetimeEpoch'];if($at<=$observed||count($periods)>=6)continue;
                $local=(new \DateTimeImmutable('@'.$at))->setTimezone($zone);
                if((int)$local->format('G')%6!==0)continue;
                $periods[]=['at'=>WeatherValues::iso($at),'temperature_c'=>WeatherValues::number($hour['temp'] ?? null,-100,70),'condition'=>WeatherValues::condition(is_string($hour['icon'] ?? null)?$hour['icon']:null)];
            }
        }
        $wind=$current['windspeed'] ?? null;
        $payload=['current'=>['temperature_c'=>WeatherValues::number($current['temp'] ?? null,-100,70),'condition'=>WeatherValues::condition(is_string($current['icon'] ?? null)?$current['icon']:null),'weather_code'=>null,'apparent_temperature_c'=>WeatherValues::number($current['feelslike'] ?? null,-120,90),'humidity_percent'=>WeatherValues::number($current['humidity'] ?? null,0,100),'wind_speed_ms'=>WeatherValues::number($wind===null?null:$wind/3.6,0,200),'source_time'=>WeatherValues::iso($observed)],'days'=>$days,'periods'=>$periods,'attribution'=>'Visual Crossing Weather'];
        return ['payload'=>$payload,'data_as_of'=>WeatherValues::iso($observed),'fetched_at'=>$fetched,'valid_until'=>WeatherValues::iso($now+1800)];
    }
}
