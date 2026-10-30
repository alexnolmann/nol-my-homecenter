<?php
declare(strict_types=1);
namespace MediaCentre\Providers;
/** Limits apply to decoded bytes, including bodies expanded by libcurl decompression. */
final class ResponseBuffer
{
    public const MAX_BYTES=2097152;
    public string $body='';
    public bool $overflow=false;
    public ?int $retryAfter=null;
    public ?string $expiresAt=null;
    public function append(string $chunk): int
    {
        if(strlen($this->body)+strlen($chunk)>self::MAX_BYTES){$this->overflow=true;return 0;}
        $this->body.=$chunk;return strlen($chunk);
    }
    public function header(string $line,?int $now=null): int
    {
        if(preg_match('/^HTTP\/\S+\s+\d{3}\b/i',$line)){$this->retryAfter=null;$this->expiresAt=null;}
        if(preg_match('/^Expires:\s*(.*?)\s*$/i',$line,$m)){
            $date=\DateTimeImmutable::createFromFormat('!D, d M Y H:i:s \G\M\T',$m[1],new \DateTimeZone('UTC'));
            if($date && $date->format('D, d M Y H:i:s \G\M\T')===$m[1])$this->expiresAt=$date->format('Y-m-d\TH:i:s\Z');
        }
        if(preg_match('/^Retry-After:\s*(.*?)\s*$/i',$line,$m)){
            if(preg_match('/^[0-9]+$/D',$m[1]))$this->retryAfter=min(86400,max(1,(int)$m[1]));
            else{
                $date=\DateTimeImmutable::createFromFormat('!D, d M Y H:i:s \G\M\T',$m[1],new \DateTimeZone('UTC'));
                if($date && $date->format('D, d M Y H:i:s \G\M\T')===$m[1])$this->retryAfter=min(86400,max(1,$date->getTimestamp()-($now ?? time())));
            }
        }
        return strlen($line);
    }
}
