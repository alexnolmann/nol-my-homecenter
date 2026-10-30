<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
use MediaCentre\Finance\{CbrRatesProvider,CbrCollector,QuoteRepository,InstrumentRepository,Decimal};
use MediaCentre\Providers\{Transport,HttpResult,ProviderError};
use MediaCentre\Storage\Database;
$xml=static function(string $date,string $nominal,string $value):string{return '<?xml version="1.0"?><ValCurs Date="'.$date.'"><Valute ID="synthetic"><CharCode>USD</CharCode><Nominal>'.$nominal.'</Nominal><Value>'.$value.'</Value></Valute><Valute><CharCode>EUR</CharCode><Nominal>1</Nominal><Value>120,4567</Value></Valute><Valute><CharCode>CNY</CharCode><Nominal>10</Nominal><Value>140,1234</Value></Valute></ValCurs>';};
$current=file_get_contents(dirname(__DIR__,2).'/contracts/providers/h4/cbr-current.xml');$previous=file_get_contents(dirname(__DIR__,2).'/contracts/providers/h4/cbr-previous.xml');
$parsed=CbrRatesProvider::parse($current);
if($parsed['rates']['USD']['price']!=='123.456789012345' || $parsed['rates']['CNY']['price']!=='14.01234')throw new RuntimeException('Exact nominal division lost.');
foreach(['<!DOCTYPE x [<!ENTITY e SYSTEM "file:///secret">]>'.$current,str_replace('26.09.2026','31.02.2026',$current),$xml('26.09.2026','0','1,0'),$xml('26.09.2026','1','1e3'),$xml('26.09.2026','1','-1,0'),str_replace('</ValCurs>','<Valute><CharCode>USD</CharCode><Nominal>1</Nominal><Value>1,0</Value></Valute></ValCurs>',$current)] as $bad){try{CbrRatesProvider::parse($bad);throw new LogicException('Invalid CBR XML accepted.');}catch(InvalidArgumentException){}}
$http=new class($current,$previous) implements Transport {
    public int $requests=0;public bool $failPrevious=false;public bool $failAll=false;public array $urls=[];
    public function __construct(private string $current,private string $previous){}
    public function get(string $url):HttpResult{$this->urls[]=$url;$n=$this->requests++;if($this->failAll || ($this->failPrevious && $n%2===1))throw new ProviderError('TIMEOUT');return new HttpResult($n%2===0?$this->current:$this->previous,'2026-09-27T11:00:00Z');}
};
$result=(new CbrRatesProvider($http))->fetch('2026-09-27');$quote=$result['quotes']['USD'];
if($quote['reference_date']!=='2026-09-24' || $quote['effective_date']!=='2026-09-26' || $quote['nominal']!=='1' || $quote['previous_price']!=='120.0123456789' || !str_contains($http->urls[1],'25%2F09%2F2026') || $quote['data_as_of']===$quote['fetched_at'])throw new RuntimeException('Previous published date or source dates lost.');
$http->failPrevious=true;$partial=(new CbrRatesProvider($http))->fetch('2026-09-27');
if(!$partial['partial'] || $partial['quotes']['USD']['previous_price']!==null || $partial['quotes']['USD']['price']!==$quote['price'])throw new RuntimeException('Reference failure erased valid current rate.');
$db=new Database($pdo);$source=MediaCentre\Shared\Uuid::random();$id=MediaCentre\Shared\Uuid::random();
(new MediaCentre\Integration\IntegrationRepository($db))->create($source,'cbr',['data_mode'=>'official']);
$fields=['integration_id'=>$source,'type'=>'currency','symbol'=>'USD','exchange'=>'','board'=>'','quote_currency'=>'RUB','name'=>'test USD'];(new InstrumentRepository($db))->create($id,$fields);$instrument=(new InstrumentRepository($db))->find($id);
$http->failPrevious=false;$collector=new CbrCollector($db,$http);if($collector->collect([$instrument],'2026-09-27')['saved']!==1)throw new RuntimeException('Rate storage failed.');
$before=(new QuoteRepository($db))->find($id);if(Decimal::canonical($before['price'])!==$quote['price'] || $before['change_pct']===null)throw new RuntimeException('Exact rate/comparison not saved.');
$http->failAll=true;try{$collector->collect([$instrument],'2026-09-27');throw new LogicException('Current CBR failure hidden.');}catch(ProviderError){}
$after=(new QuoteRepository($db))->find($id);foreach(['price','fetched_at','effective_date','reference_date'] as $key)if($before[$key]!==$after[$key])throw new RuntimeException('Error erased or rejuvenated last rate.');
$http->failAll=false;(new InstrumentRepository($db))->update($id,1,$fields);
if($collector->collect([$instrument],'2026-09-27')['invalidated']!==1 || (new QuoteRepository($db))->find($id)!==null)throw new RuntimeException('Stale currency revision accepted.');
echo "PASS H4 CBR: safe XML, exact Value/Nominal, verified previous published date, current/reference failures, persisted decimal comparison and stale result rejection.\n";
