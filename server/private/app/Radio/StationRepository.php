<?php
declare(strict_types=1);
namespace MediaCentre\Radio;
use MediaCentre\Storage\Database;
final class StationRepository
{
    public function __construct(private Database $db){}
    public function create(string $id,string $name,string $url,array $genres,string $country,bool $enabled=true): void
    {
        $values=\MediaCentre\Admin\FormModel::station(['name'=>$name,'stream_url'=>$url,'genres'=>$genres,'country_code'=>$country])->values;
        $name=$values['name'];$url=$values['stream_url'];$genres=$values['genres'];
        $this->db->transaction(function()use($id,$name,$url,$genres,$country,$enabled):void{
            $this->db->query('INSERT INTO radio_stations(id,name,stream_url,genres,country_code,enabled) VALUES(?,?,?,?,?,?)',[$id,$name,$url,Database::json($genres),$country,(int)$enabled]);
            $this->bumpCatalogue();
            $this->db->audit('create','station',$id,['name','stream_url','genres','country_code','enabled'],null,1);
        });
    }
    private function bumpCatalogue(): void
    {
        $this->db->query('INSERT IGNORE INTO app_state(id,server_id) VALUES(1,?)',[\MediaCentre\Shared\Uuid::random()]);
        $this->db->query('UPDATE app_state SET radio_revision=radio_revision+1 WHERE id=1');
    }
    public function update(string $id,int $expected,string $name,string $url,array $genres,string $country,bool $enabled): int
    {
        $values=\MediaCentre\Admin\FormModel::station(['name'=>$name,'stream_url'=>$url,'genres'=>$genres,'country_code'=>$country,'enabled'=>$enabled,'revision'=>$expected],true)->values;
        $name=$values['name'];$url=$values['stream_url'];$genres=$values['genres'];
        return $this->db->transaction(function()use($id,$expected,$name,$url,$genres,$country,$enabled):int{
            $row=$this->db->one('SELECT revision FROM radio_stations WHERE id=? FOR UPDATE',[$id]);
            if(!$row)throw new \OutOfBoundsException('Station not found.');
            if($expected<1 || (int)$row['revision']!==$expected)throw new \MediaCentre\Storage\RevisionConflict('Station revision conflict.');
            $this->db->query('UPDATE radio_stations SET name=?,stream_url=?,genres=?,country_code=?,enabled=?,revision=revision+1 WHERE id=?',[$name,$url,Database::json($genres),$country,(int)$enabled,$id]);
            $this->bumpCatalogue();
            $this->db->audit('update','station',$id,['name','stream_url','genres','country_code','enabled'],$expected,$expected+1);
            return $expected+1;
        });
    }
    public function find(string $id): ?array {return $this->db->one('SELECT * FROM radio_stations WHERE id=?',[$id]);}
    public function listForClient(string $client): array {return $this->db->all('SELECT s.*, f.sort_order favorite_order FROM radio_stations s LEFT JOIN client_station_favorites f ON f.station_id=s.id AND f.client_id=? ORDER BY s.name,s.id',[$client]);}
}
