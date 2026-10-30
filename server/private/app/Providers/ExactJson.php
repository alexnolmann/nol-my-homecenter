<?php
declare(strict_types=1);
namespace MediaCentre\Providers;
/** Preserve numeric JSON lexemes before PHP can convert prices to binary float. */
final class ExactJson
{
    public static function number(string $token): string
    {
        $negative=str_starts_with($token,'-');$token=ltrim($token,'-');$parts=preg_split('/[eE]/',$token);$exponent=(int)($parts[1] ?? 0);
        if(abs($exponent)>50 || strlen($parts[0])>100)throw new \InvalidArgumentException('Numeric lexeme outside bounds.');
        $pieces=explode('.',$parts[0]);$digits=implode('',$pieces);$point=strlen($pieces[0])+$exponent;
        if($point<=0)$value='0.'.str_repeat('0',-$point).$digits;
        elseif($point>=strlen($digits))$value=$digits.str_repeat('0',$point-strlen($digits));
        else $value=substr($digits,0,$point).'.'.substr($digits,$point);
        $pieces=explode('.',$value);$whole=ltrim($pieces[0],'0')?:'0';$fraction=rtrim($pieces[1] ?? '','0');$value=$whole.($fraction===''?'':'.'.$fraction);
        return $negative && $value!=='0'?'-'.$value:$value;
    }
    public static function decode(string $body): array
    {
        if(strlen($body)>2097152)throw new \InvalidArgumentException('JSON response too large.');
        $quoted=preg_replace_callback('~"(?:[^"\\\\]|\\\\.)*"|-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?~s',static fn($m)=>$m[0][0]==='"'?$m[0]:json_encode(self::number($m[0]),JSON_THROW_ON_ERROR),$body);
        $result=json_decode($quoted,true,64,JSON_THROW_ON_ERROR);
        if(!is_array($result) || array_is_list($result))throw new \InvalidArgumentException('JSON object required.');
        return $result;
    }
}
