<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
use MediaCentre\Finance\{CbrRatesProvider,MoexQuotesProvider,MoexCollector,InstrumentRepository,QuoteRepository,Decimal};
use MediaCentre\Providers\{Transport,HttpResult,ProviderError,ExactJson};
use MediaCentre\Storage\Database;
use MediaCentre\Shared\Uuid;
$assert=static fn(bool $ok,string $message)=>$ok ?: throw new RuntimeException($message);
$root=dirname(__DIR__,2);
$current=(string)file_get_contents($root.'/contracts/providers/h4/cbr-current.xml');
$previous=(string)file_get_contents($root.'/contracts/providers/h4/cbr-previous.xml');
$cbrHttp=new class($current,$previous) implements Transport {
    public array $urls=[];
    public function __construct(public string $current,public string $previous){}
    public function get(string $url):HttpResult{$this->urls[]=$url;return new HttpResult(count($this->urls)%2===1?$this->current:$this->previous,'2026-09-27T11:00:00Z');}
};
$holiday=(new CbrRatesProvider($cbrHttp))->fetch('2026-09-27');
$assert($holiday['quotes']['CNY']['price']==='14.01234' && $holiday['quotes']['CNY']['nominal']==='1' && $holiday['quotes']['CNY']['payload']['source_nominal']==='10','Comma/nominal conversion is not exactly once');
$assert($holiday['quotes']['USD']['effective_date']==='2026-09-26' && $holiday['quotes']['USD']['reference_date']==='2026-09-24','Nonpublication day invented a new effective/reference date');
$assert(str_contains($cbrHttp->urls[1],'25%2F09%2F2026'),'Reference lookup did not step back from actual publication date');
$cbrHttp->current=str_replace('26.09.2026','28.09.2026',$current);$cbrHttp->urls=[];
try{(new CbrRatesProvider($cbrHttp))->fetch('2026-09-27');throw new LogicException('Tomorrow rate accepted as today');}catch(ProviderError $e){$assert($e->reason==='INVALID_RESPONSE','Unexpected future rate error');}
$cbrHttp->current=$current;$cbrHttp->previous=str_replace('24.09.2026','26.09.2026',$previous);$cbrHttp->urls=[];
$partial=(new CbrRatesProvider($cbrHttp))->fetch('2026-09-27');
$assert($partial['partial'] && $partial['quotes']['USD']['price']===$holiday['quotes']['USD']['price'] && $partial['quotes']['USD']['previous_price']===null && $partial['quotes']['USD']['comparison_basis']==='none','Invalid reference erased current price or invented comparison');

$body=(string)file_get_contents($root.'/contracts/providers/h4/moex.json');
$raw=ExactJson::decode($body);
$security=MoexQuotesProvider::rows($raw,'securities')[0];$market=MoexQuotesProvider::rows($raw,'marketdata')[0];$version=MoexQuotesProvider::rows($raw,'dataversion')[0];$fetched='2026-09-27T11:00:00Z';
$closed=MoexQuotesProvider::normalize($security,$market,$version,$fetched);
$assert($closed['market_state']==='closed' && $closed['quote_as_of']==='2026-09-26T15:59:54Z' && $closed['payload']['observed_delay_seconds']===null,'Old close was redated or observed delay invented');
foreach(['no_trades','no_last','no_trade_date','future_trade','bad_time'] as $case){
    $m=$market;$v=$version;
    switch($case){case 'no_trades':$m['NUMTRADES']='0';break;case 'no_last':$m['LAST']=null;break;case 'no_trade_date':$v['trade_date']=null;break;case 'future_trade':$v['trade_date']='2026-09-28';$m['SYSTIME']='2026-09-28 19:00:17';break;case 'bad_time':$m['TIME']='25:10:00';break;}
    $assert(MoexQuotesProvider::normalize($security,$m,$v,$fetched)['price']===null,"Unconfirmed trade displayed a price: $case");
}
$http=new class($body,$fetched) implements Transport {
    public function __construct(public string $body,public string $at){}
    public function get(string $url):HttpResult{return new HttpResult($this->body,$this->at);}
};
// A malformed numeric value is isolated to BAD; the other quote stays usable.
$raw['securities']['data'][1][2]='SUR';$raw['marketdata']['data'][1][2]='broken';$http->body=json_encode($raw,JSON_THROW_ON_ERROR);
$batch=(new MoexQuotesProvider($http))->fetch('TQBR',['TEST','BAD']);
$assert(isset($batch['quotes']['TEST']) && ($batch['errors']['BAD'] ?? null)==='INVALID_INSTRUMENT_RESPONSE','One malformed item erased the good batch member');
$db=new Database($pdo);$source=Uuid::random();$ids=[];
try{
    (new MediaCentre\Integration\IntegrationRepository($db))->create($source,'moex_iss',['data_mode'=>'public_delayed']);
    $instruments=new InstrumentRepository($db);$quotes=new QuoteRepository($db);$captured=[];
    foreach(['TEST','BAD'] as $symbol){$id=Uuid::random();$ids[]=$id;$instruments->create($id,['integration_id'=>$source,'type'=>'share','symbol'=>$symbol,'exchange'=>'MOEX','board'=>'TQBR','quote_currency'=>'RUB','name'=>'H6 '.$symbol]);$captured[]=$instruments->find($id);$quotes->save($id,1,$closed);}
    $badBefore=$quotes->find($ids[1]);$collector=new MoexCollector($db,$http);$outcome=$collector->collect($captured);
    $assert($outcome['saved']===1 && $outcome['missing']===1,'Collector did not persist good member independently');
    $badAfter=$quotes->find($ids[1]);foreach(['price','fetched_at','quote_as_of','data_as_of'] as $key)$assert($badBefore[$key]===$badAfter[$key],"Broken item changed previous $key");
    $assert($badAfter['last_error_code']==='INVALID_INSTRUMENT_RESPONSE','Broken item missing error status');
    // Identity mismatch is rejected before any quote from that response can be stored.
    $raw['marketdata']['data'][0][1]='TQTF';$http->body=json_encode($raw,JSON_THROW_ON_ERROR);$goodBefore=$quotes->find($ids[0]);
    try{$collector->collect($captured);throw new LogicException('Wrong BOARDID accepted');}catch(ProviderError $e){$assert($e->reason==='INVALID_RESPONSE','Unexpected identity error');}
    $goodAfter=$quotes->find($ids[0]);foreach(['price','fetched_at','quote_as_of'] as $key)$assert($goodBefore[$key]===$goodAfter[$key],"Wrong board changed previous $key");
    foreach(['missing_price','missing_date','same_date','zero_base'] as $case){
        $q=$closed;
        switch($case){case 'missing_price':$q['previous_price']=null;break;case 'missing_date':$q['reference_date']=null;break;case 'same_date':$q['reference_date']='2026-09-26';break;case 'zero_base':$q['previous_price']='0';break;}
        $q['delay_seconds']=null;$quotes->save($ids[0],1,$q);$row=$quotes->find($ids[0]);
        $assert(Decimal::canonical($row['price'])===$closed['price'] && $row['change_pct']===null && $row['delay_seconds']===null,"Missing reference/delay invented comparison: $case");
    }
    $before=$quotes->find($ids[0]);$missing=$closed;$missing['price']=null;$missing['fetched_at']='2026-09-27T11:01:00Z';
    $quotes->save($ids[0],1,$missing);$after=$quotes->find($ids[0]);
    $assert($before['price']===$after['price'] && $before['fetched_at']===$after['fetched_at'] && $after['last_error_code']==='PRICE_UNAVAILABLE','Missing new trade erased or rejuvenated the last confirmed price');
    echo "PASS H6 finance: exact comma/nominal, nonpublication day, future rate rejection, invalid reference, missing/future trades, old close, unknown delay, wrong BOARDID, isolated malformed item and preserved last quote in real MySQL.\n";
}finally{
    foreach($ids as $id){$db->query('DELETE FROM finance_quotes WHERE instrument_id=?',[$id]);$db->query('DELETE FROM finance_instruments WHERE id=?',[$id]);}
    $db->query('DELETE FROM integrations WHERE id=?',[$source]);
}
