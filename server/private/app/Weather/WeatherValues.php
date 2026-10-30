<?php
declare(strict_types=1);
namespace MediaCentre\Weather;
use MediaCentre\Finance\Decimal;
final class WeatherValues
{
    public static function number(mixed $value,float $min,float $max): ?string
    {
        if($value===null)return null;
        if((!is_int($value)&&!is_float($value)) || !is_finite((float)$value) || $value<$min || $value>$max)throw new \InvalidArgumentException('Invalid weather number.');
        return Decimal::canonical(number_format($value,3,'.',''));
    }
    public static function iso(int $time): string {return gmdate('Y-m-d\TH:i:s\Z',$time);}
    public static function timestamp(mixed $value): int
    {
        if(!is_string($value)||!preg_match('/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$/D',$value))throw new \InvalidArgumentException('Invalid weather time.');
        $date=\DateTimeImmutable::createFromFormat('!Y-m-d\TH:i:s\Z',$value,new \DateTimeZone('UTC'));
        if(!$date||$date->format('Y-m-d\TH:i:s\Z')!==$value)throw new \InvalidArgumentException('Invalid weather time.');
        return $date->getTimestamp();
    }
    public static function condition(?string $icon): string
    {
        if($icon===null)return 'unknown';
        $icon=strtolower($icon);
        if(str_contains($icon,'thunder'))return 'thunderstorm';
        if(str_contains($icon,'sleet')||str_contains($icon,'freezing'))return 'freezing_rain';
        if(str_contains($icon,'snow'))return 'snow';
        if(str_contains($icon,'drizzle'))return 'drizzle';
        if(str_contains($icon,'rain')||str_contains($icon,'showers'))return 'rain';
        if(str_contains($icon,'fog'))return 'fog';
        if(str_contains($icon,'partly')||str_contains($icon,'fair'))return 'partly_cloudy';
        if(str_contains($icon,'cloud'))return 'cloudy';
        if(str_contains($icon,'clear')||str_contains($icon,'sunny'))return 'clear';
        return 'unknown';
    }
}
