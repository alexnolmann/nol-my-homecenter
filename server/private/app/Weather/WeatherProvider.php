<?php
declare(strict_types=1);
namespace MediaCentre\Weather;
use MediaCentre\Providers\{Transport,ProviderError};
use MediaCentre\Finance\Decimal;
final class WeatherProvider
{
    public function __construct(private Transport $http){}
    public function fetch(array $location): array
    {
        $url='https://api.open-meteo.com/v1/forecast?'.http_build_query(['latitude'=>$location['latitude'],'longitude'=>$location['longitude'],'timezone'=>$location['timezone'],'forecast_days'=>14,'timeformat'=>'unixtime','temperature_unit'=>'celsius','wind_speed_unit'=>'ms','precipitation_unit'=>'mm','current'=>'temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m','daily'=>'weather_code,temperature_2m_min,temperature_2m_max,precipitation_sum','hourly'=>'temperature_2m,weather_code']);
        $result=$this->http->get($url);
        try{return self::normalize(json_decode($result->body,true,64,JSON_THROW_ON_ERROR),$location,$result->fetchedAt);}
        catch(\InvalidArgumentException|\JsonException|\TypeError){throw new ProviderError('INVALID_RESPONSE');}
    }
    private static function value(mixed $value,float $min,float $max): ?string
    {
        if($value===null)return null;
        if((!is_int($value) && !is_float($value)) || !is_finite((float)$value) || $value<$min || $value>$max)throw new \InvalidArgumentException('Weather number out of range.');
        return Decimal::canonical(number_format($value,3,'.',''));
    }
    public static function condition(mixed $code): string
    {
        if($code!==null && (!is_int($code) || $code<0 || $code>999))throw new \InvalidArgumentException('Numeric WMO code required.');
        return match($code){0=>'clear',1,2=>'partly_cloudy',3=>'cloudy',45,48=>'fog',51,53,55=>'drizzle',56,57,66,67=>'freezing_rain',61,63,65,80,81,82=>'rain',71,73,75,77,85,86=>'snow',95,96,97,99=>'thunderstorm',default=>'unknown'};
    }
    public static function normalize(array $raw,array $location,string $fetched): array
    {
        $time=\MediaCentre\Shared\Time::sql($fetched);$now=strtotime($time.' UTC');$zone=new \DateTimeZone($location['timezone']);
        if(($raw['timezone'] ?? null)!==$location['timezone'] || !is_int($raw['utc_offset_seconds'] ?? null) || abs($raw['utc_offset_seconds'])>50400)throw new \InvalidArgumentException('Weather timezone mismatch.');
        $c=$raw['current'] ?? [];$d=$raw['daily'] ?? [];$cu=$raw['current_units'] ?? [];$du=$raw['daily_units'] ?? [];
        foreach(['temperature_2m'=>'°C','apparent_temperature'=>'°C','relative_humidity_2m'=>'%','wind_speed_10m'=>'m/s','time'=>'unixtime'] as $key=>$unit)if(($cu[$key] ?? null)!==$unit)throw new \InvalidArgumentException('Current units changed.');
        foreach(['temperature_2m_min'=>'°C','temperature_2m_max'=>'°C','precipitation_sum'=>'mm','time'=>'unixtime'] as $key=>$unit)if(($du[$key] ?? null)!==$unit)throw new \InvalidArgumentException('Daily units changed.');
        if(!is_int($c['time'] ?? null) || $c['time']>$now || $c['time']<$now-86400)throw new \InvalidArgumentException('Unconfirmed current time.');
        $count=count($d['time'] ?? []);
        if(!in_array($count,[7,14],true))throw new \InvalidArgumentException('Seven or fourteen days required.');
        foreach(['time','weather_code','temperature_2m_min','temperature_2m_max','precipitation_sum'] as $key)if(!is_array($d[$key] ?? null) || !array_is_list($d[$key]) || count($d[$key])!==$count)throw new \InvalidArgumentException('Aligned days required.');
        $today=(new \DateTimeImmutable('@'.$c['time']))->setTimezone($zone)->format('Y-m-d');$days=[];
        for($i=0;$i<$count;$i++){
            if(!is_int($d['time'][$i]))throw new \InvalidArgumentException('Daily timestamp required.');
            // Open-Meteo requires adding the returned offset when deriving dates from UNIX daily values.
            $date=gmdate('Y-m-d',$d['time'][$i]+$raw['utc_offset_seconds']);
            if($date!==(new \DateTimeImmutable($today))->modify('+'.$i.' days')->format('Y-m-d'))throw new \InvalidArgumentException('Local forecast dates not consecutive.');
            $min=self::value($d['temperature_2m_min'][$i],-100,70);$max=self::value($d['temperature_2m_max'][$i],-100,70);
            if($min!==null && $max!==null && bccomp($min,$max,3)>0)throw new \InvalidArgumentException('Minimum exceeds maximum.');
            $days[]=['date'=>$date,'temp_min_c'=>$min,'temp_max_c'=>$max,'precipitation_mm'=>self::value($d['precipitation_sum'][$i],0,3000),'condition'=>self::condition($d['weather_code'][$i]),'weather_code'=>$d['weather_code'][$i]];
        }
        $periods=[];$hourly=$raw['hourly'] ?? null;
        if($hourly!==null){
            if(!is_array($hourly) || ($raw['hourly_units']['time'] ?? null)!=='unixtime' || ($raw['hourly_units']['temperature_2m'] ?? null)!=='°C')throw new \InvalidArgumentException('Hourly units changed.');
            $hours=count($hourly['time'] ?? []);
            if($hours!==$count*24)throw new \InvalidArgumentException('Hourly timeline incomplete.');
            foreach(['time','temperature_2m','weather_code'] as $key)if(!is_array($hourly[$key] ?? null) || !array_is_list($hourly[$key]) || count($hourly[$key])!==$hours)throw new \InvalidArgumentException('Hourly values misaligned.');
            $previous=null;
            for($i=0;$i<$hours;$i++){
                $at=$hourly['time'][$i];if(!is_int($at) || ($previous!==null && $at-$previous!==3600))throw new \InvalidArgumentException('Hourly timestamps not consecutive.');$previous=$at;
                $local=(new \DateTimeImmutable('@'.$at))->setTimezone($zone);
                if($at>$c['time'] && (int)$local->format('G')%6===0 && count($periods)<6)$periods[]=['at'=>gmdate('Y-m-d\TH:i:s\Z',$at),'temperature_c'=>self::value($hourly['temperature_2m'][$i],-100,70),'condition'=>self::condition($hourly['weather_code'][$i])];
            }
        }
        return ['payload'=>['current'=>['temperature_c'=>self::value($c['temperature_2m'] ?? null,-100,70),'condition'=>self::condition($c['weather_code'] ?? null),'weather_code'=>$c['weather_code'] ?? null,'apparent_temperature_c'=>self::value($c['apparent_temperature'] ?? null,-120,90),'humidity_percent'=>self::value($c['relative_humidity_2m'] ?? null,0,100),'wind_speed_ms'=>self::value($c['wind_speed_10m'] ?? null,0,200),'source_time'=>gmdate('Y-m-d\TH:i:s\Z',$c['time'])],'days'=>$days,'periods'=>$periods,'attribution'=>'Open-Meteo.com'],'data_as_of'=>gmdate('Y-m-d\TH:i:s\Z',$c['time']),'fetched_at'=>$fetched,'valid_until'=>gmdate('Y-m-d\TH:i:s\Z',$now+1800)];
    }
}
