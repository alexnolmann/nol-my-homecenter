<?php
declare(strict_types=1);
namespace MediaCentre\Finance;
final class Decimal
{
    public static function value(mixed $value,int $precision=38,int $scale=12,bool $negative=false): string
    {
        if(!is_string($value) || !preg_match('/^'.($negative?'-?':'').'(0|[1-9][0-9]*)(?:\.([0-9]+))?$/D',$value,$match))throw new \InvalidArgumentException('Expected exact decimal string.');
        $parts=explode('.',ltrim($value,'-'));
        if(strlen($parts[0])>$precision-$scale || strlen($parts[1] ?? '')>$scale)throw new \InvalidArgumentException('Decimal outside storage precision.');
        return self::canonical($value);
    }
    public static function canonical(string $value): string
    {
        if(str_contains($value,'.'))$value=rtrim(rtrim($value,'0'),'.');
        return $value==='-0'?'0':$value;
    }
}
