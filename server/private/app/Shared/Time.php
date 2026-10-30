<?php
declare(strict_types=1);
namespace MediaCentre\Shared;
final class Time
{
    public static function sql(mixed $value): string
    {
        if(!is_string($value))throw new \InvalidArgumentException('UTC timestamp required.');
        $date=\DateTimeImmutable::createFromFormat('!Y-m-d\TH:i:s\Z',$value,new \DateTimeZone('UTC'));
        if(!$date || $date->format('Y-m-d\TH:i:s\Z')!==$value)throw new \InvalidArgumentException('Invalid UTC timestamp.');
        return $date->format('Y-m-d H:i:s.v');
    }
    public static function date(mixed $value): ?string
    {
        if($value===null)return null;
        if(!is_string($value))throw new \InvalidArgumentException('Date required.');
        $date=\DateTimeImmutable::createFromFormat('!Y-m-d',$value);
        if(!$date || $date->format('Y-m-d')!==$value)throw new \InvalidArgumentException('Invalid date.');
        return $value;
    }
    public static function iso(?string $value): ?string {return $value===null?null:(new \DateTimeImmutable($value,new \DateTimeZone('UTC')))->format('Y-m-d\TH:i:s\Z');}
}
