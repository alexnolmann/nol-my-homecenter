<?php
declare(strict_types=1);
namespace MediaCentre\Storage;
use MediaCentre\Shared\Time;
use MediaCentre\Finance\Decimal;
final class ReadModel
{
    public function __construct(private Database $db,private bool $testEnvironment=false){}
    private function meta(array $row,string $source,int $revision=1): array
    {
        $fetched=$row['fetched_at'] ?? null;$valid=$row['valid_until'] ?? null;
        $empty=($row['empty'] ?? false);
        $freshness=$empty?'empty':(($row['last_error_code'] ?? null)!==null || ($valid!==null && $valid<gmdate('Y-m-d H:i:s.v'))?'stale':'fresh');
        return ['test_data'=>$this->testEnvironment || (bool)($row['test_data'] ?? false),'scenario'=>'database','revision'=>max(1,$revision),'freshness'=>$freshness,'data_as_of'=>Time::iso($row['data_as_of'] ?? null),'fetched_at'=>Time::iso($fetched),'valid_until'=>Time::iso($valid),'source'=>$source];
    }
    public function load(string $client): array
    {
        return $this->db->transaction(function()use($client):array{
            $settings=(new \MediaCentre\Client\SettingsRepository($this->db))->read($client);
            if(!$settings)throw new \OutOfBoundsException('Client not initialized.');
            $state=$this->db->one('SELECT * FROM app_state WHERE id=1');
            if(!$state)throw new \OutOfBoundsException('Server identity not initialized.');
            $location=['id'=>$settings['location_id'],'name'=>$settings['location_name'],'timezone'=>$settings['timezone'],'revision'=>(int)$settings['location_revision'],'latitude'=>(string)$settings['latitude'],'longitude'=>(string)$settings['longitude']];
            $preferences=json_decode($settings['preferences'],true,32,JSON_THROW_ON_ERROR);
            $configMeta=$this->meta(['fetched_at'=>$settings['updated_at']],'local MySQL configuration',(int)$settings['revision']);
            $prefs=['data'=>['revision'=>(int)$settings['revision'],'location'=>$location,'selected_station_id'=>$settings['selected_station_id'],'radio_volume'=>(int)($preferences['radio_volume'] ?? 40),'display_title'=>$preferences['display_title'] ?? 'Мой дом'],'meta'=>$configMeta];
            $snapshot=$this->db->one('SELECT * FROM weather_snapshots WHERE location_id=? AND location_revision=?',[$location['id'],$settings['location_revision']]);
            $weatherData=$snapshot && $snapshot['payload']?json_decode($snapshot['payload'],true,64,JSON_THROW_ON_ERROR):['current'=>['temperature_c'=>null,'condition'=>'unknown'],'days'=>[]];
            $weatherData['location']=$location;
            $weather=['data'=>$weatherData,'meta'=>$this->meta(['empty'=>!($snapshot['payload'] ?? null)]+($snapshot ?? []),$snapshot['provider'] ?? 'not fetched',(int)($snapshot['revision'] ?? 1))];
            $rows=$this->db->all('SELECT i.id,i.name,i.type,i.symbol,i.board,i.quote_currency,i.enabled,w.show_on_dashboard,g.type source_type,q.* FROM client_finance_watchlist w JOIN finance_instruments i ON i.id=w.instrument_id JOIN integrations g ON g.id=i.integration_id LEFT JOIN finance_quotes q ON q.instrument_id=i.id AND q.instrument_revision=i.revision WHERE w.client_id=? ORDER BY w.sort_order',[$client]);
            $items=[];$financeRows=[];$dashboard=[];$quoteRevision=1;$test=false;
            foreach($rows as $row){
                $present=$row['enabled'] && $row['price']!==null;
                $meta=$this->meta(['empty'=>!$present]+$row,$row['source_type']);
                $details=$row['payload']?json_decode($row['payload'],true,32,JSON_THROW_ON_ERROR):[];
                $referenceError=$details['reference_error'] ?? null;
                if($referenceError!==null && !preg_match('/^[A-Z_]{1,64}$/D',(string)$referenceError))$referenceError='REFERENCE_UNAVAILABLE';
                if($present && $meta['freshness']==='fresh' && $referenceError!==null)$meta['freshness']='partial';
                $item=['id'=>$row['id'],'kind'=>$row['type'],'symbol'=>$row['symbol'],'board_id'=>$row['board']===''?null:$row['board'],'currency'=>$row['quote_currency'],'nominal'=>Decimal::canonical($row['nominal'] ?? '1'),'price'=>$present?Decimal::canonical($row['price']):null,'previous_price'=>$present && $row['previous_price']!==null?Decimal::canonical($row['previous_price']):null,'change_percent'=>$present && $row['change_pct']!==null?Decimal::canonical($row['change_pct']):null,'price_kind'=>$present?$row['price_kind']:'none','rate_date'=>$present?$row['effective_date']:null,'trade_at'=>$present?Time::iso($row['quote_as_of']):null,'data_as_of'=>Time::iso($row['data_as_of']),'fetched_at'=>Time::iso($row['fetched_at']),'valid_until'=>Time::iso($row['valid_until']),'source'=>$row['source_type'].($row['test_data']?' (synthetic)':''),'delay_minutes'=>$row['delay_seconds']===null?null:(int)ceil($row['delay_seconds']/60),'status'=>$meta['freshness'],'comparison'=>$row['comparison_basis'] ?? 'none','reference_date'=>$row['reference_date'],'market_state'=>$row['market_state'] ?? 'unknown'];
                $item['access_mode']=$row['source_type']==='cbr'?'official':(($details['access_mode'] ?? null)==='anonymous_delayed'?'anonymous_delayed':'unknown');
                $item['name']=$row['name'];$item['enabled']=(bool)$row['enabled'];
                $item['observed_delay_seconds']=null; // A documented delay does not establish a measured lag.
                $item['price_field']=$present?($row['source_type']==='cbr'?'Value/Nominal':(($details['price_field'] ?? null)==='LAST'?'LAST':null)):null;
                $item['reference_error']=$referenceError;
                $item['last_error_code']=$row['last_error_code'];
                $item['last_attempt_at']=Time::iso($row['last_attempt_at']);
                $items[]=$item;if($row['show_on_dashboard'])$dashboard[]=$item;
                $financeRows[]=$row;$quoteRevision+=(int)($row['revision'] ?? 0);$test=$test || (bool)$row['test_data'];
            }
            // Aggregate dates are the oldest available; per-instrument timestamps remain authoritative.
            $aggregate=['test_data'=>$test,'empty'=>!$items];
            foreach(['fetched_at','valid_until','data_as_of'] as $field){$values=array_column($financeRows,$field);$available=array_values(array_filter($values,fn($v)=>$v!==null));$aggregate[$field]=count($available)===count($items) && $available?min($available):null;}
            $financeMeta=$this->meta($aggregate,'CBR / MOEX ISS',(int)$settings['finance_list_revision']+$quoteRevision);
            $statuses=array_column($items,'status');
            if(in_array('empty',$statuses,true))$financeMeta['freshness']=count(array_filter($statuses,fn($s)=>$s==='empty'))===count($items)?'empty':'partial';
            elseif(in_array('stale',$statuses,true))$financeMeta['freshness']='stale';
            elseif(in_array('partial',$statuses,true))$financeMeta['freshness']='partial';
            $finance=['data'=>['base_currency'=>'RUB','list_revision'=>(int)$settings['finance_list_revision'],'items'=>$items],'meta'=>$financeMeta];
            $stations=array_map(function($s){$genres=json_decode($s['genres'],true,32,JSON_THROW_ON_ERROR);return ['id'=>$s['id'],'name'=>$s['name'],'genre'=>implode(', ',$genres),'genres'=>$genres,'country'=>$s['country_code'],'stream_url'=>$s['stream_url'],'enabled'=>(bool)$s['enabled'],'favorite'=>$s['favorite_order']!==null];},(new \MediaCentre\Radio\StationRepository($this->db))->listForClient($client));
            $stationById=array_column($stations,null,'id');$favorites=[];
            foreach((new \MediaCentre\Client\OrderedListRepository($this->db))->readFavorites($client) as $favorite)$favorites[]=$stationById[$favorite['station_id']];
            $radioMeta=$this->meta(['fetched_at'=>$state['updated_at']],'local station catalogue',(int)$state['radio_revision']+(int)$settings['favorites_revision']);
            $photoEnabled=(bool)($this->db->one('SELECT enabled FROM photoframe_settings WHERE id=1')['enabled'] ?? false);
            $bootstrap=['data'=>['api_version'=>'1','min_client_version'=>4,'server_id'=>$state['server_id'],'server_time'=>gmdate('Y-m-d\TH:i:s\Z'),'features'=>['weather'=>true,'radio'=>true,'finance'=>true,'photoframe'=>$photoEnabled,'home'=>false],'polling'=>['dashboard_seconds'=>30,'weather_seconds'=>300,'finance_seconds'=>300,'heartbeat_seconds'=>60],'settings'=>$prefs['data'],'finance_list_revision'=>(int)$settings['finance_list_revision']],'meta'=>$configMeta];
            $dashboardFinance=$finance;$dashboardFinance['data']['items']=$dashboard;
            $dashboardWeather=$weather;$dashboardWeather['data']['days']=array_slice($weather['data']['days'],0,7);
            $root=$this->meta(['test_data'=>$test || (bool)$weather['meta']['test_data'],'fetched_at'=>$aggregate['fetched_at'],'valid_until'=>$aggregate['valid_until'],'empty'=>!$items && !$snapshot],'stored snapshots',(int)$configMeta['revision']+(int)$weather['meta']['revision']+(int)$financeMeta['revision']+(int)$radioMeta['revision']);
            if($weather['meta']['freshness']==='stale' || $financeMeta['freshness']==='stale')$root['freshness']='stale';
            elseif(in_array($weather['meta']['freshness'],['empty','partial'],true) || in_array($financeMeta['freshness'],['empty','partial'],true))$root['freshness']=$weather['meta']['freshness']==='empty' && $financeMeta['freshness']==='empty'?'empty':'partial';
            return ['bootstrap'=>$bootstrap,'weather'=>$weather,'finance'=>$finance,'preferences'=>$prefs,'stations'=>$stations,'meta'=>$radioMeta,'dashboard'=>['data'=>['weather'=>$dashboardWeather,'finance'=>$dashboardFinance,'radio'=>['favorites'=>$favorites,'selected_station_id'=>$settings['selected_station_id']]],'meta'=>$root]];
        });
    }
}
