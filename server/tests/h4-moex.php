<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
use MediaCentre\Providers\{ExactJson,Transport,HttpResult,ProviderError};
use MediaCentre\Finance\{MoexQuotesProvider,MoexCollector,InstrumentRepository,QuoteRepository,Decimal};
use MediaCentre\Storage\Database;
$numbers=ExactJson::decode('{"price":1234567890123.123456789012,"exp":1.2300e-3,"large":9007199254740993,"text":"1e5 and \\"quoted\\""}');
if($numbers['price']!=='1234567890123.123456789012' || $numbers['exp']!=='0.00123' || $numbers['large']!=='9007199254740993' || $numbers['text']!=='1e5 and "quoted"')throw new RuntimeException('JSON lexeme precision lost.');
$security=['SECID'=>'TEST','BOARDID'=>'TQBR','CURRENCYID'=>'SUR','PREVLEGALCLOSEPRICE'=>'120.123456789012','PREVDATE'=>'2026-09-25','STATUS'=>'A'];
$market=['SECID'=>'TEST','BOARDID'=>'TQBR','LAST'=>'123.456789012345','TIME'=>'18:59:54','UPDATETIME'=>'19:00:00','SYSTIME'=>'2026-09-26 19:00:17','NUMTRADES'=>'5','TRADINGSTATUS'=>'N'];$version=['trade_date'=>'2026-09-26','trade_session_date'=>'2026-09-28'];$fetched='2026-09-27T11:00:00Z';
$q=MoexQuotesProvider::normalize($security,$market,$version,$fetched);
if($q['quote_as_of']!=='2026-09-26T15:59:54Z' || $q['payload']['trade_session_date']!=='2026-09-28' || $q['market_state']!=='closed' || $q['price']!==$market['LAST'] || $q['delay_seconds']!==900 || $q['payload']['observed_delay_seconds']!==null)throw new RuntimeException('MOEX dates, state or precision lost.');
foreach(['zero','date','time','missing'] as $case){$bad=$market;$v=$version;match($case){'zero'=>$bad['NUMTRADES']='0','date'=>$v['trade_date']='2026-09-27','time'=>$bad['TIME']='25:00:00','missing'=>$v['trade_date']=null};if(MoexQuotesProvider::normalize($security,$bad,$v,$fetched)['price']!==null)throw new RuntimeException('Unconfirmed LAST received invented trade date.');}
$foreign=$security;$foreign['CURRENCYID']='USD';try{MoexQuotesProvider::normalize($foreign,$market,$version,$fetched);throw new LogicException('Foreign currency accepted.');}catch(InvalidArgumentException){}
$body=file_get_contents(dirname(__DIR__,2).'/contracts/providers/h4/moex.json');$raw=ExactJson::decode($body);
if($raw['dataversion']['data'][0][2]!=='9007199254740993')throw new RuntimeException('Fixture version lost exact JSON number.');
$http=new class($body,$fetched) implements Transport{public bool $fail=false;public function __construct(private string $body,private string $at){}public function get(string $url):HttpResult{if($this->fail)throw new ProviderError('TIMEOUT');return new HttpResult($this->body,$this->at);}};
$batch=(new MoexQuotesProvider($http))->fetch('TQBR',['TEST','BAD']);if(!isset($batch['quotes']['TEST'],$batch['errors']['BAD']))throw new RuntimeException('One bad symbol erased valid batch member.');
try{(new MoexQuotesProvider($http))->fetch('TQBR',array_fill(0,11,'TEST'));throw new LogicException('Unbounded ISS batch accepted.');}catch(InvalidArgumentException){}
$db=new Database($pdo);$source=MediaCentre\Shared\Uuid::random();(new MediaCentre\Integration\IntegrationRepository($db))->create($source,'moex_iss',['data_mode'=>'public_delayed']);$repo=new InstrumentRepository($db);$captured=[];
foreach(['TEST','BAD'] as $symbol){$id=MediaCentre\Shared\Uuid::random();$repo->create($id,['integration_id'=>$source,'type'=>'share','symbol'=>$symbol,'exchange'=>'MOEX','board'=>'TQBR','quote_currency'=>'RUB','name'=>$symbol]);$captured[]=$repo->find($id);}
$result=(new MoexCollector($db,$http))->collect($captured);if($result['saved']!==1 || $result['missing']!==1)throw new RuntimeException('Independent ISS symbol persistence failed.');
$quotes=new QuoteRepository($db);$before=$quotes->find($captured[0]['id']);if(Decimal::canonical($before['price'])!==$market['LAST'] || $before['change_pct']===null)throw new RuntimeException('ISS exact value/comparison lost in MySQL.');
$http->fail=true;try{(new MoexCollector($db,$http))->collect($captured);throw new LogicException('ISS failure hidden.');}catch(ProviderError){}
$after=$quotes->find($captured[0]['id']);if($after['price']!==$before['price'] || $after['fetched_at']!==$before['fetched_at'])throw new RuntimeException('Failed ISS refresh erased or rejuvenated price.');
$http->fail=false;$fields=array_intersect_key($captured[0],array_flip(['integration_id','type','symbol','exchange','board','quote_currency','name']));$fields['board']='TQTF';$repo->update($captured[0]['id'],1,$fields);
if((new MoexCollector($db,$http))->collect($captured)['invalidated']!==1 || $quotes->find($captured[0]['id'])!==null)throw new RuntimeException('Previous board job recreated removed quote.');
echo "PASS H4 MOEX: numeric lexeme precision, named tables, identity/currency, confirmed trade date, separate session/SYSTIME, unknown/missing data, declared delay and independent symbol failure.\n";
