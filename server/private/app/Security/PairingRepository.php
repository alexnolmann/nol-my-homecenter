<?php
declare(strict_types=1);
namespace MediaCentre\Security;
use MediaCentre\Storage\Database;
use MediaCentre\Shared\Uuid;
use MediaCentre\Validation\Input;
use MediaCentre\Storage\RevisionConflict;
final class PairingRepository
{
    public const ALPHABET='23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
    public function __construct(private Database $db){}
    public function issue(string $admin,int $revision,string $location,?string $template,array $scopes): array
    {
        Input::uuid($location);if($template!==null)Input::uuid($template);
        $scopes=\MediaCentre\Admin\FormModel::rights(['scopes'=>$scopes,'revision'=>1])->values['scopes'];
        $code='';for($i=0;$i<8;$i++)$code.=self::ALPHABET[random_int(0,strlen(self::ALPHABET)-1)];$id=Uuid::random();
        return $this->db->transaction(function()use($admin,$revision,$location,$template,$scopes,$code,$id):array{
            $user=$this->db->one('SELECT enabled,auth_revision FROM admin_users WHERE id=? FOR SHARE',[$admin]);
            if(!$user || !$user['enabled'] || (int)$user['auth_revision']!==$revision)throw new RevisionConflict('Administrator access changed.');
            if(!$this->db->one('SELECT id FROM locations WHERE id=? FOR SHARE',[$location]))throw new \InvalidArgumentException('Location not found.');
            if($template!==null && !$this->db->one('SELECT client_id FROM client_settings WHERE client_id=? FOR SHARE',[$template]))throw new \InvalidArgumentException('Template client not found.');
            $this->db->query('INSERT INTO pairing_codes(id,code_hash,issued_by,expires_at,issued_scopes) VALUES(?,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 10 MINUTE),?)',[$id,hash('sha256',$code,true),$admin,Database::json($scopes)]);
            $this->db->query('INSERT INTO pairing_profiles(pairing_id,location_id,template_client_id,admin_auth_revision) VALUES(?,?,?,?)',[$id,$location,$template,$revision]);
            $this->db->securityAudit('pair_issue','pairing',$id,$admin,null,1);
            return ['code'=>$code,'expires_seconds'=>600];
        });
    }
    public function consume(string $code,string $name,string $version): ?array
    {
        if(!preg_match('/^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}$/D',$code))return null;
        $name=Input::text($name,100);$version=Input::text($version,40);
        return $this->db->transaction(function()use($code,$name,$version):?array{
            $pair=$this->db->one('SELECT p.*,t.location_id,t.template_client_id,t.admin_auth_revision FROM pairing_codes p JOIN pairing_profiles t ON t.pairing_id=p.id WHERE p.code_hash=? FOR UPDATE',[hash('sha256',$code,true)]);
            if(!$pair || $pair['used_at']!==null)return null;
            // Read database time after acquiring the row lock, including time spent waiting.
            if(!(int)$this->db->query('SELECT expires_at>UTC_TIMESTAMP(3) FROM pairing_codes WHERE id=?',[$pair['id']])->fetchColumn())return null;
            $admin=$this->db->one('SELECT enabled,auth_revision FROM admin_users WHERE id=? FOR SHARE',[$pair['issued_by']]);
            if(!$admin || !$admin['enabled'] || $admin['auth_revision']!==$pair['admin_auth_revision'])return null;
            $state=$this->db->one('SELECT server_id FROM app_state WHERE id=1 FOR SHARE');if(!$state)throw new \RuntimeException('Server not initialized.');
            $token=bin2hex(random_bytes(32));$client=Uuid::random();
            $this->db->query('INSERT INTO clients(id,name,token_hash,scopes,app_version) VALUES(?,?,?,?,?)',[$client,$name,hash('sha256',$token,true),$pair['issued_scopes'],$version]);
            $this->db->query('INSERT INTO client_settings(client_id,location_id,preferences) VALUES(?,?,?)',[$client,$pair['location_id'],Database::json(['radio_volume'=>40])]);
            if($pair['template_client_id']!==null){
                $this->db->query('SELECT client_id FROM client_settings WHERE client_id=? FOR SHARE',[$pair['template_client_id']]);
                $this->db->query('INSERT INTO client_finance_watchlist(client_id,instrument_id,sort_order,show_on_dashboard) SELECT ?,instrument_id,sort_order,show_on_dashboard FROM client_finance_watchlist WHERE client_id=?',[$client,$pair['template_client_id']]);
                $this->db->query('INSERT INTO client_station_favorites(client_id,station_id,sort_order) SELECT ?,station_id,sort_order FROM client_station_favorites WHERE client_id=?',[$client,$pair['template_client_id']]);
            }
            $this->db->query('UPDATE pairing_codes SET used_at=UTC_TIMESTAMP(3) WHERE id=?',[$pair['id']]);
            $this->db->securityAudit('pair_use','client',$client,$pair['issued_by'],null,1);
            return ['client_id'=>$client,'server_id'=>$state['server_id'],'token'=>$token,'scopes'=>json_decode($pair['issued_scopes'],true,32,JSON_THROW_ON_ERROR)];
        });
    }
    public function revoke(string $client,int $revision,string $admin): void
    {
        Input::uuid($client);Input::revision($revision);
        $this->db->transaction(function()use($client,$revision,$admin):void{
            $row=$this->db->one('SELECT auth_revision FROM clients WHERE id=? FOR UPDATE',[$client]);
            if(!$row)throw new \OutOfBoundsException('Client not found.');
            if((int)$row['auth_revision']!==$revision)throw new RevisionConflict('Client changed.');
            $this->db->query('UPDATE clients SET token_hash=NULL,revoked_at=UTC_TIMESTAMP(3),auth_revision=auth_revision+1 WHERE id=?',[$client]);
            $this->db->securityAudit('client_revoke','client',$client,$admin,$revision,$revision+1);
        });
    }
}
