<?php
declare(strict_types=1);
namespace MediaCentre\Client;
use MediaCentre\Storage\Database;
final class SettingsRepository
{
    public function __construct(private Database $db){}
    public function read(string $client): ?array {return $this->db->one('SELECT s.*,l.name location_name,l.timezone,l.latitude,l.longitude,l.revision location_revision FROM client_settings s JOIN locations l ON l.id=s.location_id WHERE s.client_id=?',[$client]);}
    public function setDisplayTitle(string $client,int $expected,mixed $title): int
    {
        $title=\MediaCentre\Validation\Input::text($title,24);
        \MediaCentre\Validation\Input::revision($expected);
        return $this->db->transaction(function()use($client,$expected,$title):int{
            $row=$this->db->lockSettings($client,$expected);
            $preferences=json_decode($row['preferences'],true,64,JSON_THROW_ON_ERROR);
            $preferences['display_title']=$title;
            $this->db->query('UPDATE client_settings SET preferences=?,revision=revision+1 WHERE client_id=?',[Database::json($preferences),$client]);
            $this->db->audit('update','settings',$client,['display_title'],$expected,$expected+1);
            return $expected+1;
        });
    }
    public function update(string $client,int $expected,array $patch): int
    {
        $patch=\MediaCentre\Admin\FormModel::preferences($patch);
        \MediaCentre\Validation\Input::revision($expected);
        if(array_diff(array_keys($patch),['location_id','selected_station_id','radio_volume']))throw new \InvalidArgumentException('Unsupported preference.');
        if(isset($patch['radio_volume']) && (!is_int($patch['radio_volume']) || $patch['radio_volume']<0 || $patch['radio_volume']>100))throw new \InvalidArgumentException('Invalid volume.');
        return $this->db->transaction(function()use($client,$expected,$patch):int{
            $row=$this->db->lockSettings($client,$expected);
            if(isset($patch['selected_station_id'])){
                $station=$this->db->one('SELECT enabled FROM radio_stations WHERE id=? FOR SHARE',[$patch['selected_station_id']]);
                if(!$station || !$station['enabled'])throw new \InvalidArgumentException('Station is unavailable.');
            }
            $preferences=json_decode($row['preferences'],true,64,JSON_THROW_ON_ERROR);
            if(isset($patch['radio_volume']))$preferences['radio_volume']=$patch['radio_volume'];
            $selected=array_key_exists('selected_station_id',$patch)?$patch['selected_station_id']:$row['selected_station_id'];
            $this->db->query('UPDATE client_settings SET location_id=?,selected_station_id=?,preferences=?,revision=revision+1 WHERE client_id=?',[$patch['location_id'] ?? $row['location_id'],$selected,Database::json($preferences),$client]);
            $this->db->audit('update','settings',$client,array_keys($patch),$expected,$expected+1);
            return $expected+1;
        });
    }
}
