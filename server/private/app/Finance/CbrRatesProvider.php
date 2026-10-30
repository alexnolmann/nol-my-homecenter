<?php
declare(strict_types=1);
namespace MediaCentre\Finance;
use MediaCentre\Providers\{Transport,ProviderError};
use MediaCentre\Shared\Time;
final class CbrRatesProvider
{
    public function __construct(private Transport $http){}
    public static function parse(string $body): array
    {
        if(strlen($body)>2097152 || stripos($body,'<!DOCTYPE')!==false || stripos($body,'<!ENTITY')!==false)throw new \InvalidArgumentException('Unsafe CBR XML.');
        $old=libxml_use_internal_errors(true);
        try{$xml=simplexml_load_string($body,\SimpleXMLElement::class,LIBXML_NONET);}
        finally{libxml_clear_errors();libxml_use_internal_errors($old);}
        if(!$xml || $xml->getName()!=='ValCurs')throw new \InvalidArgumentException('Invalid CBR root.');
        $sourceDate=(string)$xml['Date'];$date=\DateTimeImmutable::createFromFormat('!d.m.Y',$sourceDate,new \DateTimeZone('Europe/Moscow'));
        if(!$date || $date->format('d.m.Y')!==$sourceDate)throw new \InvalidArgumentException('Invalid CBR effective date.');
        $rates=[];
        foreach($xml->Valute as $row){
            $code=(string)$row->CharCode;if(!in_array($code,['USD','EUR','CNY'],true))continue;
            if(isset($rates[$code]))throw new \InvalidArgumentException('Duplicate CBR currency.');
            $nominal=Decimal::value((string)$row->Nominal,24,8);$source=Decimal::value(str_replace(',','.',(string)$row->Value));
            if(!preg_match('/^[1-9][0-9]*$/D',$nominal) || bccomp($source,'0',12)<=0)throw new \InvalidArgumentException('Positive rate and nominal required.');
            $rates[$code]=['price'=>Decimal::canonical(bcdiv($source,$nominal,12)),'source_value'=>$source,'source_nominal'=>$nominal];
        }
        return ['date'=>$date->format('Y-m-d'),'rates'=>$rates];
    }
    private function daily(string $date): array
    {
        $day=new \DateTimeImmutable(Time::date($date));
        $result=$this->http->get('https://www.cbr.ru/scripts/XML_daily.asp?'.http_build_query(['date_req'=>$day->format('d/m/Y')]));
        try{$value=self::parse($result->body);if($value['date']>$date)throw new \InvalidArgumentException('CBR returned future effective date.');}
        catch(\InvalidArgumentException){throw new ProviderError('INVALID_RESPONSE');}
        return $value+['fetched_at'=>$result->fetchedAt];
    }
    public function fetch(?string $requested=null): array
    {
        $requested ??=(new \DateTimeImmutable('now',new \DateTimeZone('Europe/Moscow')))->format('Y-m-d');
        $current=$this->daily($requested);$previous=null;$referenceError=null;
        $previousRequest=(new \DateTimeImmutable($current['date']))->modify('-1 day')->format('Y-m-d');
        try{$previous=$this->daily($previousRequest);if($previous['date']>=$current['date'])throw new ProviderError('INVALID_REFERENCE');}
        catch(ProviderError $error){$previous=null;$referenceError=$error->reason;}
        $fetched=$current['fetched_at'];$quotes=[];
        foreach($current['rates'] as $code=>$rate){
            $ref=$previous['rates'][$code] ?? null;
            $quotes[$code]=['price'=>$rate['price'],'nominal'=>'1','effective_date'=>$current['date'],'previous_price'=>$ref['price'] ?? null,'reference_date'=>$ref?$previous['date']:null,'comparison_basis'=>$ref?'previous_published_rate':'none','quote_as_of'=>null,
                'data_as_of'=>(new \DateTimeImmutable($current['date'].' 00:00:00',new \DateTimeZone('Europe/Moscow')))->setTimezone(new \DateTimeZone('UTC'))->format('Y-m-d\TH:i:s\Z'),'fetched_at'=>$fetched,'valid_until'=>gmdate('Y-m-d\TH:i:s\Z',strtotime($fetched)+21600),'source_timezone'=>'Europe/Moscow','market_state'=>'not_applicable',
                'payload'=>$rate+['requested_date'=>$requested,'effective_date'=>$current['date'],'previous_requested_date'=>$previousRequest,'previous_effective_date'=>$ref?$previous['date']:null,'reference_error'=>$referenceError ?? ($ref?null:'REFERENCE_UNAVAILABLE')]];
        }
        return ['quotes'=>$quotes,'partial'=>$referenceError!==null || count($quotes)!==3,'reference_error'=>$referenceError];
    }
}
