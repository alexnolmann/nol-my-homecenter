<?php
declare(strict_types=1);
namespace MediaCentre\Weather;
use MediaCentre\Security\SecretStore;
use MediaCentre\Storage\{Database,RevisionConflict};
final class WeatherSettingsRepository
{
    public const PROVIDERS=['open_meteo'=>'Open-Meteo','visual_crossing'=>'Visual Crossing','met_norway'=>'MET Norway'];
    private const OBJECT='00000000-0000-4000-8000-000000000006';
    public function __construct(private Database $db,private ?SecretStore $secrets=null){}
    private function secrets(): SecretStore
    {
        return $this->secrets ??= new SecretStore(getenv('MC_SECRET_KEY_PATH') ?: dirname(__DIR__,4).'/.local/h2/secrets/master.key');
    }
    public function state(bool $lock=false): array
    {
        if($lock)$this->db->query("INSERT IGNORE INTO weather_provider_settings(id,provider,revision) VALUES(1,'open_meteo',1)");
        $row=$this->db->one('SELECT provider,revision,key_id FROM weather_provider_settings WHERE id=1'.($lock?' FOR UPDATE':''));
        return ['provider'=>$row['provider'] ?? 'open_meteo','revision'=>(int)($row['revision'] ?? 1),'has_visual_crossing_key'=>($row['key_id'] ?? null)!==null];
    }
    public function key(): ?string
    {
        $row=$this->db->one('SELECT credentials_ciphertext,nonce,key_id FROM weather_provider_settings WHERE id=1');
        if(!$row || $row['key_id']===null)return null;
        $values=$this->secrets()->open(self::OBJECT,$row);
        $key=$values['visual_crossing_key'] ?? null;
        if(!is_string($key) || !preg_match('/^[A-Za-z0-9_-]{8,128}$/D',$key))throw new \RuntimeException('Weather credential invalid.');
        return $key;
    }
    public function save(string $provider,int $expected,?string $newKey,bool $clear): int
    {
        if(!array_key_exists($provider,self::PROVIDERS) || $expected<1 || $expected===PHP_INT_MAX || $clear&&$newKey!==null)throw new \InvalidArgumentException('Invalid weather settings.');
        if($newKey!==null && !preg_match('/^[A-Za-z0-9_-]{8,128}$/D',$newKey))throw new \InvalidArgumentException('Invalid API key.');
        $sealed=$newKey===null?null:$this->secrets()->seal(self::OBJECT,['visual_crossing_key'=>$newKey]);
        return $this->db->transaction(function()use($provider,$expected,$newKey,$clear,$sealed):int{
            $row=$this->db->one('SELECT provider,revision,credentials_ciphertext,nonce,key_id FROM weather_provider_settings WHERE id=1 FOR UPDATE');
            $revision=(int)($row['revision'] ?? 1);if($revision!==$expected)throw new RevisionConflict('Weather provider revision conflict.');
            $hasKey=$newKey!==null || (!$clear && ($row['key_id'] ?? null)!==null);
            if($provider==='visual_crossing' && !$hasKey)throw new \InvalidArgumentException('Visual Crossing API key required.');
            $changed=$row===null || $provider!==$row['provider'] || $newKey!==null || $clear&&$row['key_id']!==null;
            if(!$changed)return $revision;
            $cipher=$sealed['credentials_ciphertext'] ?? ($clear?null:($row['credentials_ciphertext'] ?? null));
            $nonce=$sealed['nonce'] ?? ($clear?null:($row['nonce'] ?? null));
            $keyId=$sealed['key_id'] ?? ($clear?null:($row['key_id'] ?? null));
            if($row===null)$this->db->query('INSERT INTO weather_provider_settings(id,provider,credentials_ciphertext,nonce,key_id,revision) VALUES(1,?,?,?,?,?)',[$provider,$cipher,$nonce,$keyId,$revision+1]);
            else $this->db->query('UPDATE weather_provider_settings SET provider=?,credentials_ciphertext=?,nonce=?,key_id=?,revision=revision+1 WHERE id=1',[$provider,$cipher,$nonce,$keyId]);
            if($row===null||$provider!==$row['provider']||$provider==='visual_crossing'&&($newKey!==null||$clear))
                $this->db->query("UPDATE weather_snapshots SET last_error_code='PROVIDER_CHANGED',revision=revision+1 WHERE payload IS NOT NULL");
            $this->db->audit($row===null?'create':'update','integration',self::OBJECT,['type','credentials'],$row===null?null:$revision,$revision+1);
            return $revision+1;
        });
    }
}
