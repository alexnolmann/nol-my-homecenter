<?php
declare(strict_types=1);
namespace MediaCentre\Providers;
final class CurlTransport implements Transport
{
    public int $requests=0;
    public function get(string $url): HttpResult
    {
        $parts=parse_url($url);
        $allowed=['api.open-meteo.com'=>['/v1/forecast'],'api.met.no'=>['/weatherapi/locationforecast/2.0/compact'],'weather.visualcrossing.com'=>null,'www.cbr.ru'=>['/scripts/XML_daily.asp','/scripts/XML_dynamic.asp'],'iss.moex.com'=>null];
        $host=$parts['host'] ?? '';$path=$parts['path'] ?? '';
        if(($parts['scheme'] ?? '')!=='https' || !array_key_exists($host,$allowed) || isset($parts['user'],$parts['pass']) || isset($parts['user']) || isset($parts['fragment']) || (isset($parts['port']) && $parts['port']!==443) ||
           ($host==='iss.moex.com'?!preg_match('~^/iss/engines/stock/markets/shares/boards/[A-Z0-9_-]{1,32}/securities(?:/[A-Z0-9._-]{1,32})?\.json$~D',$path):
           ($host==='weather.visualcrossing.com'?!preg_match('~^/VisualCrossingWebServices/rest/services/timeline/-?[0-9]+(?:\.[0-9]{1,6})?,-?[0-9]+(?:\.[0-9]{1,6})?$~D',$path):!in_array($path,$allowed[$host],true))))throw new \InvalidArgumentException('Fixed official HTTPS endpoint required.');
        $response=new ResponseBuffer();$curl=curl_init($url);$this->requests++;
        curl_setopt_array($curl,[CURLOPT_CONNECTTIMEOUT=>4,CURLOPT_TIMEOUT=>15,CURLOPT_FOLLOWLOCATION=>false,CURLOPT_SSL_VERIFYPEER=>true,CURLOPT_SSL_VERIFYHOST=>2,CURLOPT_PROTOCOLS=>CURLPROTO_HTTPS,CURLOPT_ENCODING=>'',CURLOPT_USERAGENT=>'MediaCentre/0.8 (+https://github.com/alexnolmann/nol-my-homecenter)',
            CURLOPT_HEADERFUNCTION=>static fn($handle,string $line):int=>$response->header($line),
            CURLOPT_WRITEFUNCTION=>static fn($handle,string $chunk):int=>$response->append($chunk)]);
        try{
            $ok=curl_exec($curl);$status=(int)curl_getinfo($curl,CURLINFO_RESPONSE_CODE);$errno=curl_errno($curl);
            if($response->overflow)throw new ProviderError('RESPONSE_TOO_LARGE');
            if($ok===false)throw new ProviderError($errno===CURLE_OPERATION_TIMEDOUT?'TIMEOUT':'TRANSPORT_ERROR');
            if($status!==200)throw new ProviderError($status===429?'RATE_LIMITED':'HTTP_ERROR',$response->retryAfter);
            return new HttpResult($response->body,gmdate('Y-m-d\TH:i:s\Z'),$response->expiresAt);
        }finally{curl_close($curl);}
    }
}
