<?php
declare(strict_types=1);
namespace MediaCentre\Client;
use MediaCentre\Storage\Database;
final class ClientRepository
{
    public function __construct(private Database $db){}
    public function create(string $id,string $name,string $location,array $scopes=[]): void
    {
        \MediaCentre\Admin\FormModel::rights(['scopes'=>$scopes,'revision'=>1]);
        $name=\MediaCentre\Validation\Input::text($name,128);
        $this->db->transaction(function()use($id,$name,$location,$scopes):void{
            $this->db->query('INSERT INTO clients(id,name,scopes) VALUES(?,?,?)',[$id,$name,Database::json($scopes)]);
            $this->db->query('INSERT INTO client_settings(client_id,location_id,preferences) VALUES(?,?,?)',[$id,$location,Database::json(['radio_volume'=>40])]);
            $this->db->audit('create','client',$id,['name','scopes','location_id'],null,1);
        });
    }
    public function find(string $id): ?array {return $this->db->one('SELECT id,name,scopes,auth_revision,revoked_at FROM clients WHERE id=?',[$id]);}
    public function heartbeat(string $id,string $version): void {$this->db->query('UPDATE clients SET app_version=?,last_seen_at=UTC_TIMESTAMP(3) WHERE id=?',[$version,$id]);}
}
