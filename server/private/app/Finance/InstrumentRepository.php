<?php
declare(strict_types=1);
namespace MediaCentre\Finance;
use MediaCentre\Storage\Database;
use MediaCentre\Storage\RevisionConflict;
final class InstrumentRepository
{
    public function __construct(private Database $db){}
    public static function identity(array $fields): array
    {
        foreach(['integration_id','type','symbol','exchange','board','quote_currency','name'] as $key)if(!is_string($fields[$key] ?? null))throw new \InvalidArgumentException('Instrument identity required.');
        $fields['integration_id']=strtolower($fields['integration_id']);
        if(!preg_match('/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/D',$fields['integration_id']))throw new \InvalidArgumentException('Invalid source ID.');
        foreach(['symbol','exchange','board','quote_currency'] as $key)$fields[$key]=strtoupper(trim($fields[$key]));
        if($fields['quote_currency']!=='RUB' || !in_array($fields['type'],['currency','share'],true))throw new \InvalidArgumentException('Unsupported market/currency.');
        if($fields['type']==='currency'){
            if(!in_array($fields['symbol'],['USD','EUR','CNY'],true) || $fields['exchange']!=='' || $fields['board']!=='')throw new \InvalidArgumentException('Invalid currency identity.');
        }elseif($fields['exchange']!=='MOEX' || !preg_match('/^[A-Z0-9._-]{1,32}$/D',$fields['symbol']) || !preg_match('/^[A-Z0-9_-]{1,32}$/D',$fields['board']))throw new \InvalidArgumentException('MOEX identity required.');
        $fields['instrument_key']=implode(':',array_map(fn($key)=>$fields[$key],['integration_id','type','symbol','exchange','board','quote_currency']));
        return $fields;
    }
    private function source(array $fields): void
    {
        $source=$this->db->one('SELECT type FROM integrations WHERE id=? FOR SHARE',[$fields['integration_id']]);
        if(!$source || $source['type']!==($fields['type']==='currency'?'cbr':'moex_iss'))throw new \InvalidArgumentException('Incompatible source.');
    }
    public function create(string $id,array $fields,bool $enabled=true): void
    {
        \MediaCentre\Validation\Input::keys($fields,['integration_id','type','symbol','exchange','board','quote_currency','name']);
        $fields['name']=\MediaCentre\Validation\Input::text($fields['name'] ?? null,255);
        $fields=self::identity($fields);
        $this->db->transaction(function()use($id,$fields,$enabled):void{
            $this->source($fields);
            $this->db->query('INSERT INTO finance_instruments(id,instrument_key,integration_id,type,symbol,exchange,board,name,quote_currency,enabled) VALUES(?,?,?,?,?,?,?,?,?,?)',[$id,$fields['instrument_key'],$fields['integration_id'],$fields['type'],$fields['symbol'],$fields['exchange'],$fields['board'],$fields['name'],$fields['quote_currency'],(int)$enabled]);
            $this->db->audit('create','instrument',$id,['identity','name','enabled'],null,1);
        });
    }
    public function find(string $id): ?array {return $this->db->one('SELECT * FROM finance_instruments WHERE id=?',[$id]);}
    public function update(string $id,int $expected,array $fields,?bool $enabled=null): int
    {
        \MediaCentre\Validation\Input::keys($fields,['integration_id','type','symbol','exchange','board','quote_currency','name']);
        $fields['name']=\MediaCentre\Validation\Input::text($fields['name'] ?? null,255);
        \MediaCentre\Validation\Input::revision($expected);
        $fields=self::identity($fields);
        return $this->db->transaction(function()use($id,$expected,$fields,$enabled):int{
            $row=$this->db->one('SELECT * FROM finance_instruments WHERE id=? FOR UPDATE',[$id]);
            if(!$row)throw new \OutOfBoundsException('Instrument not found.');
            if($expected<1 || (int)$row['revision']!==$expected)throw new RevisionConflict('Instrument revision conflict.');
            $this->source($fields);
            $this->db->query('UPDATE finance_instruments SET instrument_key=?,integration_id=?,type=?,symbol=?,exchange=?,board=?,name=?,quote_currency=?,enabled=?,revision=revision+1 WHERE id=?',[$fields['instrument_key'],$fields['integration_id'],$fields['type'],$fields['symbol'],$fields['exchange'],$fields['board'],$fields['name'],$fields['quote_currency'],(int)($enabled ?? $row['enabled']),$id]);
            // Even a name-only edit invalidates the captured job revision.
            $this->db->query('DELETE FROM finance_quotes WHERE instrument_id=?',[$id]);
            $this->db->audit('update','instrument',$id,$enabled===null?['identity','name']:['identity','name','enabled'],$expected,$expected+1);
            return $expected+1;
        });
    }
}
