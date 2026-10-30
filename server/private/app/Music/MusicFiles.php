<?php
declare(strict_types=1);
namespace MediaCentre\Music;
/** Files stay outside the web root. Resolve containment again for every stream. */
final class MusicFiles
{
    public static function root(string $path): string
    {
        if(strlen($path)>2048 || str_contains($path,"\0") || !preg_match('~^(?:[A-Za-z]:[\\\\/]|/|\\\\\\\\)~D',$path))throw new \InvalidArgumentException('Укажите абсолютный путь к каталогу.');
        $root=realpath($path);
        if($root===false || !is_dir($root) || !is_readable($root))throw new \InvalidArgumentException('Каталог недоступен серверу. Проверьте путь и права чтения.');
        $root=rtrim(str_replace('\\','/',$root),'/');
        if($root==='' || preg_match('~^[A-Za-z]:$~D',$root))throw new \InvalidArgumentException('Выберите каталог с музыкой, а не корень диска.');
        return $root;
    }
    public static function resolve(string $root,string $relative): ?string
    {
        if(str_contains($relative,"\0") || str_contains($relative,'\\') || str_starts_with($relative,'/') || preg_match('~(?:^|/)\.\.(?:/|$)~',$relative))return null;
        $base=realpath($root);$path=realpath($root.($relative===''?'':'/'.$relative));
        if($base===false || $path===false)return null;
        $base=rtrim(str_replace('\\','/',$base),'/');$path=str_replace('\\','/',$path);
        $configured=rtrim(str_replace('\\','/',$root),'/');
        if((PHP_OS_FAMILY==='Windows'?strtolower($base)!==strtolower($configured):$base!==$configured))return null;
        $a=PHP_OS_FAMILY==='Windows'?strtolower($path):$path;$b=PHP_OS_FAMILY==='Windows'?strtolower($base):$base;
        return $a===$b || str_starts_with($a,$b.'/')?$path:null;
    }
    public static function id(string $library,string $relative): string
    {
        $h=hash('sha256',$library."\0".(PHP_OS_FAMILY==='Windows'?mb_strtolower($relative,'UTF-8'):$relative));
        return substr($h,0,8).'-'.substr($h,8,4).'-5'.substr($h,13,3).'-a'.substr($h,17,3).'-'.substr($h,20,12);
    }
    public static function internalUri(string $path): string
    {
        $path=str_replace('\\','/',$path);
        if(preg_match('~^([A-Za-z]):/(.+)$~D',$path,$m))return '/__music_drive/'.$m[1].'/'.implode('/',array_map('rawurlencode',explode('/',$m[2])));
        if(str_starts_with($path,'//'))return '/__music_unc/'.implode('/',array_map('rawurlencode',explode('/',substr($path,2))));
        if(str_starts_with($path,'/'))return '/__music_file/'.implode('/',array_map('rawurlencode',explode('/',substr($path,1))));
        throw new \InvalidArgumentException('Absolute music path required.');
    }
    /** Bounded ID3v2 text reader; never load audio into memory. */
    public static function metadata(string $path): ?array
    {
        $f=@fopen($path,'rb');if(!$f)return null;
        try{
            $header=fread($f,10);$tags=[];
            if(strlen($header)<4)return null;
            if(substr($header,0,3)==='ID3'){
                $version=ord($header[3]);$size=0;for($i=6;$i<10;$i++)$size=($size<<7)|(ord($header[$i])&127);
                if($size>1024*1024 || !in_array($version,[3,4],true))return ['name'=>pathinfo($path,PATHINFO_FILENAME),'artist'=>null,'album'=>null];
                $data=fread($f,min($size,131072));$pos=0;
                if(ord($header[5])&64)return ['name'=>pathinfo($path,PATHINFO_FILENAME),'artist'=>null,'album'=>null];
                while($pos+10<=strlen($data)){
                    $id=substr($data,$pos,4);if(!preg_match('/^[A-Z0-9]{4}$/D',$id))break;
                    $length=$version===4?0:unpack('N',substr($data,$pos+4,4))[1];
                    if($version===4)for($i=4;$i<8;$i++)$length=($length<<7)|(ord($data[$pos+$i])&127);
                    $flags=substr($data,$pos+8,2);$pos+=10;if($length<1 || $length>strlen($data)-$pos)break;
                    if(in_array($id,['TIT2','TPE1','TALB'],true) && $flags==="\0\0"){
                        $raw=substr($data,$pos+1,$length-1);$encoding=ord($data[$pos]);
                        $text=match($encoding){0=>mb_convert_encoding($raw,'UTF-8','ISO-8859-1'),1=>mb_convert_encoding($raw,'UTF-8','UTF-16'),2=>mb_convert_encoding($raw,'UTF-8','UTF-16BE'),3=>$raw,default=>''};
                        $text=trim(preg_replace('/[\x00-\x1f\x7f]/u',' ',mb_convert_encoding($text,'UTF-8','UTF-8')) ?? '');
                        if($text!=='')$tags[$id]=mb_substr($text,0,255,'UTF-8');
                    }$pos+=$length;
                }
            }elseif(ord($header[0])!==255 || (ord($header[1])&224)!==224)return null;
            return ['name'=>$tags['TIT2'] ?? mb_substr(pathinfo($path,PATHINFO_FILENAME),0,255,'UTF-8'),'artist'=>$tags['TPE1'] ?? null,'album'=>$tags['TALB'] ?? null];
        }finally{fclose($f);}
    }
}
