<?php
declare(strict_types=1);
namespace MediaCentre\Finance;
use MediaCentre\Providers\{Transport,ProviderError,ExactJson};
use MediaCentre\Shared\Time;
final class MoexQuotesProvider
{
    public const BATCH=10;
    public function __construct(private Transport $http){}
    public static function rows(array $raw,string $block): array
    {
        $table=$raw[$block] ?? null;
        if(!is_array($table) || !is_array($table['columns'] ?? null) || !is_array($table['data'] ?? null) || !array_is_list($table['columns']) || !array_is_list($table['data']) || count($table['data'])>self::BATCH)throw new \InvalidArgumentException('ISS table schema changed.');
        foreach($table['columns'] as $column)if(!is_string($column))throw new \InvalidArgumentException('Named ISS columns required.');
        if(count(array_unique($table['columns']))!==count($table['columns']))throw new \InvalidArgumentException('Duplicate ISS column.');
        $rows=[];foreach($table['data'] as $row){if(!is_array($row) || !array_is_list($row) || count($row)!==count($table['columns']))throw new \InvalidArgumentException('ISS row schema changed.');$rows[]=array_combine($table['columns'],$row);}return $rows;
    }
    public function fetch(string $board,array $symbols): array
    {
        if(!preg_match('/^[A-Z0-9_-]{1,32}$/D',$board) || !$symbols || count($symbols)>self::BATCH || count(array_unique($symbols))!==count($symbols))throw new \InvalidArgumentException('Bounded ISS board batch required.');
        foreach($symbols as $symbol)if(!is_string($symbol) || !preg_match('/^[A-Z0-9._-]{1,32}$/D',$symbol))throw new \InvalidArgumentException('SECID required.');
        $result=$this->http->get('https://iss.moex.com/iss/engines/stock/markets/shares/boards/'.$board.'/securities.json?'.http_build_query(['securities'=>implode(',',$symbols),'iss.only'=>'securities,marketdata,dataversion','iss.meta'=>'off','securities.columns'=>'SECID,BOARDID,PREVLEGALCLOSEPRICE,PREVDATE,CURRENCYID,STATUS','marketdata.columns'=>'SECID,BOARDID,LAST,TIME,UPDATETIME,SYSTIME,TRADINGSTATUS,NUMTRADES,TRADEDATE']));
        try{
            $raw=ExactJson::decode($result->body);$securities=self::indexed(self::rows($raw,'securities'),$board,$symbols);$market=self::indexed(self::rows($raw,'marketdata'),$board,$symbols);$versions=self::rows($raw,'dataversion');
            if(count($versions)!==1)throw new \InvalidArgumentException('One ISS data version required.');
        }catch(\InvalidArgumentException|\JsonException|\TypeError){throw new ProviderError('INVALID_RESPONSE');}
        $quotes=[];$errors=[];
        foreach($symbols as $symbol){
            if(!isset($securities[$symbol],$market[$symbol])){$errors[$symbol]='INSTRUMENT_UNAVAILABLE';continue;}
            try{$quotes[$symbol]=self::normalize($securities[$symbol],$market[$symbol],$versions[0],$result->fetchedAt);}
            catch(\InvalidArgumentException|\TypeError){$errors[$symbol]='INVALID_INSTRUMENT_RESPONSE';}
        }
        return ['quotes'=>$quotes,'errors'=>$errors];
    }
    private static function indexed(array $rows,string $board,array $symbols): array
    {
        $result=[];foreach($rows as $row){$symbol=$row['SECID'] ?? null;if(($row['BOARDID'] ?? null)!==$board || !in_array($symbol,$symbols,true) || isset($result[$symbol]))throw new \InvalidArgumentException('Unexpected ISS identity.');$result[$symbol]=$row;}return $result;
    }
    private static function moment(?string $date,mixed $time): ?string
    {
        if($date===null || !is_string($time) || !preg_match('/^([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$/D',$time))return null;
        return (new \DateTimeImmutable($date.' '.$time,new \DateTimeZone('Europe/Moscow')))->setTimezone(new \DateTimeZone('UTC'))->format('Y-m-d\TH:i:s\Z');
    }
    public static function normalize(array $security,array $market,array $version,string $fetched): array
    {
        if(!in_array($security['CURRENCYID'] ?? null,['SUR','RUB'],true))throw new \InvalidArgumentException('RUB quote required.');
        $last=($market['LAST'] ?? null)===null?null:Decimal::value($market['LAST']);
        $previous=($security['PREVLEGALCLOSEPRICE'] ?? null)===null?null:Decimal::value($security['PREVLEGALCLOSEPRICE']);
        $reference=Time::date($security['PREVDATE'] ?? null);$date=Time::date($version['trade_date'] ?? null);$session=Time::date($version['trade_session_date'] ?? null);
        if(isset($market['TRADEDATE'])){$explicit=Time::date($market['TRADEDATE']);if($date!==null && $explicit!==$date)throw new \InvalidArgumentException('ISS trading dates disagree.');$date=$explicit;}
        $system=$market['SYSTIME'] ?? null;$systemDate=null;$sourceAt=null;
        if(is_string($system) && preg_match('/^(\d{4}-\d{2}-\d{2}) ([0-9:]{8})$/D',$system,$m)){$systemDate=Time::date($m[1]);$sourceAt=self::moment($systemDate,$m[2]);}
        $trade=self::moment($date,$market['TIME'] ?? null);$now=Time::sql($fetched);
        $hasTrades=is_string($market['NUMTRADES'] ?? null) && preg_match('/^[0-9]+$/D',$market['NUMTRADES']) && bccomp($market['NUMTRADES'],'0',0)>0;
        if(!$hasTrades || $date!==$systemDate || $trade===null || $sourceAt===null || $trade>$sourceAt || Time::sql($trade)>$now || Time::sql($sourceAt)>$now)$trade=null;
        if($sourceAt!==null && Time::sql($sourceAt)>$now)$sourceAt=null;
        $price=$trade===null?null:$last;
        $state=match($market['TRADINGSTATUS'] ?? null){'T'=>'open','N','C'=>'closed',default=>'unknown'};
        return ['price'=>$price,'nominal'=>'1','previous_price'=>$previous,'reference_date'=>$reference,'comparison_basis'=>'previous_close','quote_as_of'=>$trade,'data_as_of'=>$sourceAt,'fetched_at'=>$fetched,'valid_until'=>gmdate('Y-m-d\TH:i:s\Z',strtotime($fetched)+1800),'source_timezone'=>'Europe/Moscow','delay_seconds'=>900,'market_state'=>$state,
            'payload'=>['SECID'=>$security['SECID'],'BOARDID'=>$security['BOARDID'],'currency'=>$security['CURRENCYID'],'LAST'=>$last,'PREVLEGALCLOSEPRICE'=>$previous,'PREVDATE'=>$reference,'trade_date'=>$date,'trade_session_date'=>$session,'TIME'=>$market['TIME'] ?? null,'SYSTIME'=>$system,'UPDATETIME'=>$market['UPDATETIME'] ?? null,'NUMTRADES'=>$market['NUMTRADES'] ?? null,'TRADINGSTATUS'=>$market['TRADINGSTATUS'] ?? null,'security_status'=>$security['STATUS'] ?? null,'access_mode'=>'anonymous_delayed','documented_delay_seconds'=>900,'observed_delay_seconds'=>null,'price_field'=>$price===null?null:'LAST']];
    }
}
