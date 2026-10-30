<?php
declare(strict_types=1);
namespace MediaCentre\Climate;
use MediaCentre\Storage\Database;
use MediaCentre\Storage\RevisionConflict;
use MediaCentre\Validation\Input;

/** Client identity is supplied only by authenticated API/admin code. */
final class ClimateRepository
{
    public function __construct(private Database $db){}
    private function lock(string $client,?int $revision=null): array
    {
        Input::uuid($client);
        $this->db->query('INSERT IGNORE INTO client_climate_settings(client_id) VALUES(?)',[$client]);
        $row=$this->db->one('SELECT * FROM client_climate_settings WHERE client_id=? FOR UPDATE',[$client]);
        if(!$row)throw new \OutOfBoundsException('Планшет не найден.');
        if($revision!==null && ((int)$row['revision']!==$revision || $revision<1))throw new RevisionConflict('Climate configuration changed.');
        return $row;
    }
    private function stateInside(string $client,array $row): array
    {
        $items=$this->db->all('SELECT * FROM climate_sensors WHERE client_id=? ORDER BY position,address',[$client]);
        $sensors=array_map(static fn(array $s):array=>[
            'address'=>$s['address'],'name'=>$s['name'],
            'latest'=>$s['measured_at']===null?null:['temperature'=>(float)$s['temperature'],'humidity'=>(int)$s['humidity'],'battery_millivolts'=>(int)$s['battery_millivolts'],'measured_at'=>(int)$s['measured_at']],
            'received_at'=>$s['received_at'],'status'=>$s['last_status']
        ],$items);
        return ['revision'=>(int)$row['revision'],'poll_minutes'=>(int)$row['poll_minutes'],'initialized'=>(bool)$row['initialized'],'sensors'=>$sensors];
    }
    public function state(string $client): array
    {
        return $this->db->transaction(fn():array=>$this->stateInside($client,$this->lock($client)));
    }
    private static function configuration(array $sensors,int $minutes): array
    {
        if($minutes<1 || $minutes>120 || count($sensors)>3 || !array_is_list($sensors))throw new \InvalidArgumentException('Интервал: 1–120 минут; не более трёх датчиков.');
        $seen=[];$result=[];
        foreach($sensors as $sensor){
            if(!is_array($sensor) || array_diff(array_keys($sensor),['address','name']) || !is_string($sensor['address'] ?? null) || !is_string($sensor['name'] ?? null))throw new \InvalidArgumentException('Укажите название и Bluetooth ID.');
            $address=strtoupper(trim($sensor['address']));$name=trim($sensor['name']);
            if(!preg_match('/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/D',$address) || isset($seen[$address]))throw new \InvalidArgumentException('Bluetooth ID должен быть уникальным MAC-адресом.');
            if(preg_match('//u',$name)!==1 || preg_match_all('/./us',$name)<1 || preg_match_all('/./us',$name)>40 || preg_match('/[\x00-\x1f\x7f]/u',$name))throw new \InvalidArgumentException('Название: от 1 до 40 символов.');
            $seen[$address]=true;$result[]=['address'=>$address,'name'=>$name];
        }
        return $result;
    }
    public function replace(string $client,int $revision,int $minutes,array $sensors): array
    {
        $sensors=self::configuration($sensors,$minutes);
        return $this->db->transaction(function()use($client,$revision,$minutes,$sensors):array{
            $row=$this->lock($client,$revision);
            if($minutes!==(int)$row['poll_minutes'])throw new \InvalidArgumentException('Интервал меняется в панели управления.');
            if($revision>=PHP_INT_MAX)throw new \OverflowException('Revision exhausted.');
            $existing=$this->db->all('SELECT address FROM climate_sensors WHERE client_id=?',[$client]);
            $addresses=array_column($sensors,'address');
            foreach($existing as $item)if(!in_array($item['address'],$addresses,true))$this->db->query('DELETE FROM climate_sensors WHERE client_id=? AND address=?',[$client,$item['address']]);
            foreach($sensors as $position=>$sensor)$this->db->query('INSERT INTO climate_sensors(client_id,address,name,position) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE name=?,position=?',[$client,$sensor['address'],$sensor['name'],$position,$sensor['name'],$position]);
            $this->db->query('UPDATE client_climate_settings SET revision=revision+1,poll_minutes=?,initialized=TRUE WHERE client_id=?',[$minutes,$client]);
            $this->db->audit('replace','settings',$client,['settings','items'],$revision,$revision+1);
            $row['revision']=$revision+1;$row['poll_minutes']=$minutes;$row['initialized']=true;
            return $this->stateInside($client,$row);
        });
    }
    /** Admin changes one field against a revision, preserving measurements and all other fields. */
    public function edit(string $client,int $revision,string $action,array $form): void
    {
        $this->db->transaction(function()use($client,$revision,$action,$form):void{
            $state=$this->stateInside($client,$this->lock($client,$revision));
            $sensors=array_map(static fn($s)=>['address'=>$s['address'],'name'=>$s['name']],$state['sensors']);
            $minutes=$state['poll_minutes'];$address=strtoupper(trim($form['address'] ?? ''));
            if($action==='climate_interval'){
                if(!is_string($form['poll_minutes'] ?? null) || !preg_match('/^[0-9]{1,3}$/D',$form['poll_minutes']))throw new \InvalidArgumentException('Интервал: 1–120 минут.');
                $minutes=(int)$form['poll_minutes'];
            }elseif($action==='climate_add')$sensors[]=['address'=>$address,'name'=>$form['name'] ?? ''];
            else{
                $found=false;
                foreach($sensors as $n=>$sensor)if($sensor['address']===$address){$found=true;if($action==='climate_delete')unset($sensors[$n]);else $sensors[$n]['name']=$form['name'] ?? '';break;}
                if(!$found)throw new \InvalidArgumentException('Датчик уже удалён. Обновите страницу.');
            }
            // replace() owns its transaction; inline write after shared validation.
            $sensors=self::configuration(array_values($sensors),$minutes);
            if($revision>=PHP_INT_MAX)throw new \OverflowException('Revision exhausted.');
            $addresses=array_column($sensors,'address');
            foreach($state['sensors'] as $s)if(!in_array($s['address'],$addresses,true))$this->db->query('DELETE FROM climate_sensors WHERE client_id=? AND address=?',[$client,$s['address']]);
            foreach($sensors as $position=>$s)$this->db->query('INSERT INTO climate_sensors(client_id,address,name,position) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE name=?,position=?',[$client,$s['address'],$s['name'],$position,$s['name'],$position]);
            $this->db->query('UPDATE client_climate_settings SET revision=revision+1,poll_minutes=?,initialized=? WHERE client_id=?',[$minutes,$action==='climate_interval'?(int)$state['initialized']:1,$client]);
            $this->db->audit('update','settings',$client,['settings','items'],$revision,$revision+1);
        });
    }
    public function report(string $client,int $revision,array $items): void
    {
        if(!array_is_list($items) || count($items)>3)throw new \InvalidArgumentException('Invalid report.');
        $seen=[];$now=(int)floor(microtime(true)*1000);
        foreach($items as $item){
            if(!is_array($item) || array_diff(array_keys($item),['address','latest','status']) || !is_string($item['address'] ?? null) || !preg_match('/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/D',$item['address']) || isset($seen[$item['address']]) || !in_array($item['status'] ?? null,['pending','ok','unavailable','bluetooth_off'],true) || !array_key_exists('latest',$item))throw new \InvalidArgumentException('Invalid report.');
            $seen[$item['address']]=true;$r=$item['latest'];
            if($r!==null && (!is_array($r) || count($r)!==4 || array_diff(array_keys($r),['temperature','humidity','battery_millivolts','measured_at']) || (!is_int($r['temperature'] ?? null) && !is_float($r['temperature'] ?? null)) || !is_int($r['humidity'] ?? null) || !is_int($r['battery_millivolts'] ?? null) || !is_int($r['measured_at'] ?? null) || !is_finite((float)$r['temperature']) || $r['temperature']<-50 || $r['temperature']>85 || $r['humidity']<0 || $r['humidity']>100 || $r['battery_millivolts']<1500 || $r['battery_millivolts']>4000 || $r['measured_at']<1 || $r['measured_at']>$now+300000))throw new \InvalidArgumentException('Invalid reading.');
        }
        $this->db->transaction(function()use($client,$revision,$items):void{
            $state=$this->stateInside($client,$this->lock($client,$revision));$known=array_column($state['sensors'],null,'address');
            foreach($items as $item)if(!isset($known[$item['address']]))throw new \InvalidArgumentException('Unknown sensor.');
            foreach($items as $item){
                $r=$item['latest'];$old=$known[$item['address']]['latest'];
                if($r!==null && ($old===null || $r['measured_at']>$old['measured_at'])){
                    $this->db->query('UPDATE climate_sensors SET temperature=?,humidity=?,battery_millivolts=?,measured_at=?,received_at=UTC_TIMESTAMP(3),last_status=? WHERE client_id=? AND address=?',[$r['temperature'],$r['humidity'],$r['battery_millivolts'],$r['measured_at'],$item['status'],$client,$item['address']]);
                }else $this->db->query('UPDATE climate_sensors SET last_status=? WHERE client_id=? AND address=?',[$item['status'],$client,$item['address']]);
            }
        });
    }
}
