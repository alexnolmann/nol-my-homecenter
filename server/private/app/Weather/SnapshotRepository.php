<?php
declare(strict_types=1);
namespace MediaCentre\Weather;
use MediaCentre\Storage\Database;
use MediaCentre\Shared\Time;
final class SnapshotRepository
{
    public function __construct(private Database $db){}
    public function failure(string $location,int $revision,string $code,string $attempt,string $provider='open_meteo',?int $providerRevision=null): bool
    {
        if(!preg_match('/^[A-Z_]{1,64}$/D',$code))throw new \InvalidArgumentException('Safe error code required.');
        $attempt=Time::sql($attempt);
        return $this->db->transaction(function()use($location,$revision,$code,$attempt,$provider,$providerRevision):bool{
            $row=$this->db->one('SELECT revision FROM locations WHERE id=? FOR UPDATE',[$location]);
            if(!$row || (int)$row['revision']!==$revision)return false;
            $setting=(new WeatherSettingsRepository($this->db))->state(true);if($setting['provider']!==$provider||($providerRevision!==null&&$setting['revision']!==$providerRevision))return false;
            $this->db->query("INSERT INTO weather_snapshots(location_id,location_revision,provider,last_attempt_at,last_error_code) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE last_attempt_at=?,last_error_code=?,revision=revision+1",[$location,$revision,$provider,$attempt,$code,$attempt,$code]);
            return true;
        });
    }
    public function save(string $location,int $revision,string $provider,array $payload,string $asOf,string $fetched,string $valid,bool $test=false,?int $providerRevision=null): bool
    {
        $asOf=Time::sql($asOf);$fetched=Time::sql($fetched);$valid=Time::sql($valid);
        $json=Database::json($payload);
        if(!preg_match('/^[a-z0-9_]{1,32}$/D',$provider) || strlen($json)>262144 || $valid<$fetched || $asOf>$fetched)throw new \InvalidArgumentException('Invalid weather snapshot.');
        $check=['data'=>['location'=>['id'=>$location,'name'=>'location','timezone'=>'UTC']]+$payload,'meta'=>['test_data'=>$test,'scenario'=>'database','revision'=>max(1,$revision),'freshness'=>'fresh','data_as_of'=>Time::iso($asOf),'fetched_at'=>Time::iso($fetched),'valid_until'=>Time::iso($valid),'source'=>$provider]];
        $validator=new \MediaCentre\Shared\ContractValidator(dirname(__DIR__,4).'/contracts/v1/schema.json');
        if(!$validator->valid('weather',json_decode(json_encode($check,JSON_THROW_ON_ERROR))))throw new \InvalidArgumentException('Weather snapshot does not match contract.');
        return $this->db->transaction(function()use($location,$revision,$provider,$json,$asOf,$fetched,$valid,$test,$providerRevision):bool{
            $row=$this->db->one('SELECT revision FROM locations WHERE id=? FOR UPDATE',[$location]);
            if(!$row || (int)$row['revision']!==$revision)return false;
            if(!$test){$setting=(new WeatherSettingsRepository($this->db))->state(true);if($setting['provider']!==$provider||($providerRevision!==null&&$setting['revision']!==$providerRevision))return false;}
            $old=$this->db->one('SELECT revision,provider,fetched_at,data_as_of FROM weather_snapshots WHERE location_id=?',[$location]);
            if($old && $old['fetched_at']>$fetched)return false;
            if($old && $old['provider']===$provider && $old['data_as_of']!==null && $asOf<$old['data_as_of'])return false;
            $this->db->query('DELETE FROM weather_snapshots WHERE location_id=?',[$location]);
            $this->db->query('INSERT INTO weather_snapshots(location_id,location_revision,provider,payload,data_as_of,fetched_at,valid_until,test_data,revision) VALUES(?,?,?,?,?,?,?,?,?)',[$location,$revision,$provider,$json,$asOf,$fetched,$valid,(int)$test,($old['revision'] ?? 0)+1]);
            return true;
        });
    }
}
