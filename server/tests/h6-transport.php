<?php
declare(strict_types=1);
// Replace only the libcurl boundary in this separate test process. Production callbacks run unchanged.
namespace MediaCentre\Providers {
    final class CurlFixture {public array $options=[];public array $headers=[];public array $chunks=[];public int $status=200;public int $errno=0;public bool $closed=false;public static self $next;}
    function curl_init(string $url):CurlFixture{return CurlFixture::$next;}
    function curl_setopt_array(CurlFixture $curl,array $options):bool{$curl->options=$options;return true;}
    function curl_exec(CurlFixture $curl):bool{
        foreach($curl->headers as $line)($curl->options[CURLOPT_HEADERFUNCTION])($curl,$line);
        foreach($curl->chunks as $chunk)if(($curl->options[CURLOPT_WRITEFUNCTION])($curl,$chunk)!==strlen($chunk)){$curl->errno=CURLE_WRITE_ERROR;return false;}
        return $curl->errno===0;
    }
    function curl_getinfo(CurlFixture $curl,int $option):int{return $curl->status;}
    function curl_errno(CurlFixture $curl):int{return $curl->errno;}
    function curl_close(CurlFixture $curl):void{$curl->closed=true;}
}
namespace {
    require dirname(__DIR__).'/private/bootstrap.php';
    use MediaCentre\Providers\{CurlFixture,CurlTransport,ProviderError,ResponseBuffer};
    $assert=static fn(bool $ok,string $why)=>$ok ?: throw new \RuntimeException($why);
    foreach(['ok','429','date429','timeout','oversized','redirect'] as $case){
        $curl=new CurlFixture();CurlFixture::$next=$curl;$curl->chunks=['{"fixture":true}'];$expected=null;
        switch($case){
            case '429':$curl->status=429;$curl->headers=["HTTP/1.1 429 Too Many Requests\r\n","Retry-After: 300\r\n"];$expected='RATE_LIMITED';break;
            case 'date429':$curl->status=429;$curl->headers=['Retry-After: '.gmdate('D, d M Y H:i:s \G\M\T',time()+300)."\r\n"];$expected='RATE_LIMITED';break;
            case 'timeout':$curl->errno=CURLE_OPERATION_TIMEDOUT;$expected='TIMEOUT';break;
            case 'oversized':$curl->chunks=[str_repeat('x',ResponseBuffer::MAX_BYTES),'overflow'];$expected='RESPONSE_TOO_LARGE';break;
            case 'redirect':$curl->status=302;$expected='HTTP_ERROR';break;
        }
        $transport=new CurlTransport();
        try{$result=$transport->get('https://www.cbr.ru/scripts/XML_daily.asp');$assert($expected===null && $result->body==='{"fixture":true}','Unexpected accepted transport response');}
        catch(ProviderError $error){$assert($error->reason===$expected,'Wrong classified provider error');if($expected==='RATE_LIMITED')$assert($error->retryAfter>=298 && $error->retryAfter<=300,'Transport lost Retry-After');}
        $assert($curl->closed && $transport->requests===1,'Curl handle leaked');
        $assert($curl->options[CURLOPT_FOLLOWLOCATION]===false && $curl->options[CURLOPT_SSL_VERIFYPEER]===true && $curl->options[CURLOPT_SSL_VERIFYHOST]===2 && $curl->options[CURLOPT_TIMEOUT]===15 && $curl->options[CURLOPT_CONNECTTIMEOUT]===4,'Transport safety/time bounds changed');
    }
    echo "PASS H6 transport callbacks: bounded decoded response, 429 numeric/date backoff, timeout/error classification, no redirects, TLS verification and closed handles; libcurl boundary replaced, no external requests.\n";
}
