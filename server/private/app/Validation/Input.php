<?php
declare(strict_types=1);
namespace MediaCentre\Validation;
final class Input
{
    public static function keys(array $data,array $allowed): void
    {
        if(array_diff(array_keys($data),$allowed))throw new \InvalidArgumentException('Unsupported input fields.');
    }
    public static function text(mixed $value,int $max): string
    {
        if(!is_string($value) || preg_match('//u',$value)!==1 || trim($value)==='' || preg_match('/[\x00-\x1f\x7f]/u',$value) || preg_match_all('/./us',$value)>$max)throw new \InvalidArgumentException('Invalid text field.');
        return trim($value);
    }
    public static function uuid(mixed $value): string
    {
        if(!is_string($value) || !preg_match('/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/iD',$value))throw new \InvalidArgumentException('UUID required.');
        return strtolower($value);
    }
    public static function revision(mixed $value): int
    {
        if(!is_int($value) || $value<1 || $value===PHP_INT_MAX)throw new \InvalidArgumentException('Positive revision required.');
        return $value;
    }
    public static function timezone(mixed $value): string
    {
        if(!is_string($value) || !in_array($value,\DateTimeZone::listIdentifiers(),true))throw new \InvalidArgumentException('IANA timezone required.');
        return $value;
    }
    public static function url(mixed $value): string
    {
        $value=self::text($value,2048);$parts=parse_url($value);
        if(!$parts || !in_array(strtolower($parts['scheme'] ?? ''),['http','https'],true) || empty($parts['host']) || isset($parts['user']) || isset($parts['pass']) || isset($parts['fragment']) || preg_match('/\s/u',$value) || !filter_var($value,FILTER_VALIDATE_URL))throw new \InvalidArgumentException('HTTP(S) URL without credentials required.');
        return $value;
    }
    public static function coordinate(mixed $value,bool $latitude): string
    {
        $value=\MediaCentre\Finance\Decimal::value($value,9,6,true);$max=$latitude?'90':'180';
        if(bccomp($value,'-'.$max,6)<0 || bccomp($value,$max,6)>0)throw new \InvalidArgumentException('Coordinate out of range.');
        return $value;
    }
    public static function boolean(mixed $value): bool {if(!is_bool($value))throw new \InvalidArgumentException('Boolean required.');return $value;}
}
