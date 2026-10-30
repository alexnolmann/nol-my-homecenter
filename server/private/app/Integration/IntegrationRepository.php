<?php
declare(strict_types=1);
namespace MediaCentre\Integration;
use MediaCentre\Storage\Database;
use MediaCentre\Storage\RevisionConflict;
use MediaCentre\Security\SecretStore;
final class IntegrationRepository
{
    public function __construct(private Database $db,private ?SecretStore $secrets=null){}
    private function validate(string $type,array $settings,?array $credentials): void
    {
        if(!preg_match('/^[a-z0-9_]{1,32}$/D',$type))throw new \InvalidArgumentException('Invalid integration type.');
        \MediaCentre\Validation\Input::keys($settings,['data_mode']);
        if(isset($settings['data_mode']) && !in_array($settings['data_mode'],['unknown','public_delayed','official'],true))throw new \InvalidArgumentException('Unsupported data mode.');
        if(in_array($type,['cbr','moex_iss'],true) && $credentials!==null)throw new \InvalidArgumentException('Public sources have no credentials.');
    }
    public function create(string $id,string $type,array $settings=[],?array $credentials=null): void
    {
        $this->validate($type,$settings,$credentials);
        $record=$credentials===null?['credentials_ciphertext'=>null,'nonce'=>null,'key_id'=>null]:($this->secrets ?? throw new \LogicException('Secret store required.'))->seal($id,$credentials);
        $this->db->transaction(function()use($id,$type,$settings,$record):void{
            $this->db->query("INSERT INTO integrations(id,type,status,settings,credentials_ciphertext,nonce,key_id) VALUES(?,?,'configured',?,?,?,?)",[$id,$type,json_encode((object)$settings,JSON_THROW_ON_ERROR),$record['credentials_ciphertext'],$record['nonce'],$record['key_id']]);
            $this->db->audit('create','integration',$id,['type','settings','credentials'],null,1);
        });
    }
    // NULL means clear credentials. Callers must supply the intended complete state.
    public function update(string $id,int $expected,string $type,array $settings,?array $credentials=null): int
    {
        \MediaCentre\Validation\Input::revision($expected);
        $this->validate($type,$settings,$credentials);
        $record=$credentials===null?['credentials_ciphertext'=>null,'nonce'=>null,'key_id'=>null]:($this->secrets ?? throw new \LogicException('Secret store required.'))->seal($id,$credentials);
        return $this->db->transaction(function()use($id,$expected,$type,$settings,$record):int{
            $old=$this->db->one('SELECT type,revision FROM integrations WHERE id=? FOR UPDATE',[$id]);
            if(!$old)throw new \OutOfBoundsException('Integration missing.');
            if($expected<1 || (int)$old['revision']!==$expected)throw new RevisionConflict('Integration revision conflict.');
            if($old['type']!==$type && $this->db->one('SELECT id FROM finance_instruments WHERE integration_id=? LIMIT 1',[$id]))throw new \InvalidArgumentException('Linked provider type cannot change.');
            $this->db->query('UPDATE integrations SET type=?,settings=?,credentials_ciphertext=?,nonce=?,key_id=?,revision=revision+1 WHERE id=?',[$type,json_encode((object)$settings,JSON_THROW_ON_ERROR),$record['credentials_ciphertext'],$record['nonce'],$record['key_id'],$id]);
            // A changed access mode must not retain prices or accept jobs captured before it.
            $this->db->query('UPDATE finance_instruments SET revision=revision+1 WHERE integration_id=?',[$id]);
            $this->db->query('DELETE q FROM finance_quotes q JOIN finance_instruments i ON i.id=q.instrument_id WHERE i.integration_id=?',[$id]);
            $this->db->audit('update','integration',$id,['type','settings','credentials'],$expected,$expected+1);
            return $expected+1;
        });
    }
    public function publicView(string $id): ?array {return $this->db->one('SELECT id,type,status,settings,revision,key_id IS NOT NULL has_credentials FROM integrations WHERE id=?',[$id]);}
}
