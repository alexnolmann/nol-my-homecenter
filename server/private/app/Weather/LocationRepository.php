<?php
declare(strict_types=1);
namespace MediaCentre\Weather;
use MediaCentre\Storage\Database;
final class LocationRepository
{
    public function __construct(private Database $db){}
    public function create(string $id,string $name,string $latitude,string $longitude,string $timezone): void
    {
        $values=\MediaCentre\Admin\FormModel::location(['name'=>$name,'latitude'=>$latitude,'longitude'=>$longitude,'timezone'=>$timezone])->values;
        $name=$values['name'];$latitude=$values['latitude'];$longitude=$values['longitude'];
        $this->db->transaction(function()use($id,$name,$latitude,$longitude,$timezone):void{
            $this->db->query('INSERT INTO locations(id,name,latitude,longitude,timezone) VALUES(?,?,?,?,?)',[$id,$name,$latitude,$longitude,$timezone]);
            $this->db->audit('create','location',$id,['name','latitude','longitude','timezone'],null,1);
        });
    }
    public function find(string $id): ?array {return $this->db->one('SELECT * FROM locations WHERE id=?',[$id]);}
    public function update(string $id,int $expected,string $name,string $latitude,string $longitude,string $timezone): int
    {
        $values=\MediaCentre\Admin\FormModel::location(['name'=>$name,'latitude'=>$latitude,'longitude'=>$longitude,'timezone'=>$timezone,'revision'=>$expected],true)->values;
        $name=$values['name'];$latitude=$values['latitude'];$longitude=$values['longitude'];
        return $this->db->transaction(function()use($id,$expected,$name,$latitude,$longitude,$timezone):int{
            $row=$this->db->one('SELECT * FROM locations WHERE id=? FOR UPDATE',[$id]);
            if(!$row)throw new \OutOfBoundsException('Location not found.');
            if($expected<1 || (int)$row['revision']!==$expected)throw new \MediaCentre\Storage\RevisionConflict('Location revision conflict.');
            $this->db->query('UPDATE locations SET name=?,latitude=?,longitude=?,timezone=?,revision=revision+1 WHERE id=?',[$name,$latitude,$longitude,$timezone,$id]);
            // Old provider results are bound to the previous location revision.
            $this->db->query('DELETE FROM weather_snapshots WHERE location_id=?',[$id]);
            $this->db->audit('update','location',$id,['name','latitude','longitude','timezone'],$expected,$expected+1);
            return $expected+1;
        });
    }
}
