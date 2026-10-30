<?php
declare(strict_types=1);
namespace MediaCentre\Finance;
use MediaCentre\Storage\Database;
use MediaCentre\Shared\Time;
final class QuoteRepository
{
    public function __construct(private Database $db){}
    public function find(string $id): ?array {return $this->db->one('SELECT * FROM finance_quotes WHERE instrument_id=?',[$id]);}
    public function failure(string $id,int $revision,string $code,string $attempt): bool
    {
        if(!preg_match('/^[A-Z_]{1,64}$/D',$code))throw new \InvalidArgumentException('Safe error code required.');
        $attempt=Time::sql($attempt);
        return $this->db->transaction(function()use($id,$revision,$code,$attempt):bool{
            $instrument=$this->db->one('SELECT enabled,revision FROM finance_instruments WHERE id=? FOR UPDATE',[$id]);
            if(!$instrument || !$instrument['enabled'] || (int)$instrument['revision']!==$revision)return false;
            $this->db->query('UPDATE finance_quotes SET last_attempt_at=?,last_error_code=?,revision=revision+1 WHERE instrument_id=? AND instrument_revision=?',[$attempt,$code,$id,$revision]);
            return true;
        });
    }
    public function save(string $id,int $instrumentRevision,array $q): bool
    {
        $price=$q['price']===null?null:Decimal::value($q['price']);
        $nominal=Decimal::value($q['nominal'],24,8);
        if(bccomp($nominal,'0',8)<=0)throw new \InvalidArgumentException('Positive nominal required.');
        $previous=($q['previous_price'] ?? null)===null?null:Decimal::value($q['previous_price']);
        $basis=$q['comparison_basis'] ?? 'none';
        if(!in_array($basis,['none','previous_published_rate','previous_close'],true))throw new \InvalidArgumentException('Unknown comparison.');
        $effective=Time::date($q['effective_date'] ?? null);$reference=Time::date($q['reference_date'] ?? null);
        $trade=isset($q['quote_as_of'])?Time::sql($q['quote_as_of']):null;
        $data=isset($q['data_as_of'])?Time::sql($q['data_as_of']):null;
        $fetched=Time::sql($q['fetched_at']);$valid=Time::sql($q['valid_until']);
        if($valid<$fetched || ($trade!==null && $trade>$fetched) || ($data!==null && $data>$fetched))throw new \InvalidArgumentException('Invalid snapshot chronology.');
        $delay=$q['delay_seconds'] ?? null;
        if($delay!==null && (!is_int($delay) || $delay<0 || $delay>604800))throw new \InvalidArgumentException('Invalid declared delay.');
        $zone=$q['source_timezone'] ?? 'Europe/Moscow';
        if(!in_array($zone,\DateTimeZone::listIdentifiers(),true))throw new \InvalidArgumentException('Unknown source timezone.');
        $market=$q['market_state'] ?? 'unknown';
        if(!in_array($market,['unknown','open','closed','not_applicable'],true))throw new \InvalidArgumentException('Invalid market state.');
        return $this->db->transaction(function()use($id,$instrumentRevision,$q,$price,$nominal,$previous,$basis,$effective,$reference,$trade,$data,$fetched,$valid,$delay,$zone,$market):bool{
            $instrument=$this->db->one('SELECT * FROM finance_instruments WHERE id=? FOR UPDATE',[$id]);
            if(!$instrument || !$instrument['enabled'] || (int)$instrument['revision']!==$instrumentRevision)return false;
            $old=$this->find($id);
            if($old && $old['fetched_at']>$fetched)return false;
            if($old && $price!==null && $data!==null && $old['data_as_of']!==null && $data<$old['data_as_of'])return false;
            if($old && $price!==null && $trade!==null && $old['quote_as_of']!==null && $trade<$old['quote_as_of'])return false;
            if($price===null && $old && $old['price']!==null){
                $this->db->query("UPDATE finance_quotes SET last_attempt_at=?,last_error_code='PRICE_UNAVAILABLE',revision=revision+1 WHERE instrument_id=?",[$fetched,$id]);
                return true;
            }
            $kind=$price===null?'none':($instrument['type']==='currency'?'official_rate':'last_trade');
            if($price!==null && (($kind==='official_rate' && $effective===null) || ($kind==='last_trade' && $trade===null)))throw new \InvalidArgumentException('Original price date required.');
            $tradeDate=$trade===null?null:(new \DateTimeImmutable($trade,new \DateTimeZone('UTC')))->setTimezone(new \DateTimeZone($zone))->format('Y-m-d');
            $comparable=$price!==null && $previous!==null && $reference!==null &&
                (($instrument['type']==='currency' && $basis==='previous_published_rate' && $reference<$effective) ||
                 ($instrument['type']==='share' && $basis==='previous_close' && $tradeDate!==null && $reference<$tradeDate));
            $ref=$comparable?$previous:null;$comparison=$comparable?$basis:'none';
            $amount=$comparable?bcsub($price,$ref,12):null;
            $pct=$comparable && bccomp($ref,'0',12)>0?bcdiv(bcmul($amount,'100',24),$ref,10):null;
            if($pct!==null)$pct=Decimal::value(Decimal::canonical($pct),20,10,true);
            $payload=$q['payload'] ?? new \stdClass();
            $encoded=json_encode($payload,JSON_THROW_ON_ERROR);
            if(strlen($encoded)>65536 || !str_starts_with($encoded,'{'))throw new \InvalidArgumentException('Bounded quote object required.');
            $columns=['instrument_id','instrument_revision','price','nominal','previous_price','change_amount','change_pct','price_kind','quote_as_of','data_as_of','effective_date','reference_date','reference_at','comparison_basis','source_timezone','delay_seconds','market_state','payload','fetched_at','valid_until','test_data','revision'];
            $values=[$id,$instrumentRevision,$price,$nominal,$ref,$amount,$pct,$kind,$trade,$data,$effective,$comparable?$reference:null,null,$comparison,$zone,$delay,$market,$encoded,$fetched,$valid,(int)($q['test_data'] ?? false),($old['revision'] ?? 0)+1];
            $this->db->query('DELETE FROM finance_quotes WHERE instrument_id=?',[$id]);
            $this->db->query('INSERT INTO finance_quotes('.implode(',',$columns).') VALUES('.implode(',',array_fill(0,count($values),'?')).')',$values);
            return true;
        });
    }
}
