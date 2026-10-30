<?php
declare(strict_types=1);
namespace MediaCentre\Client;
use MediaCentre\Storage\Database;
final class OrderedListRepository
{
    public function __construct(private Database $db){}
    public function favorites(string $client,int $expected,array $ids): int
    {
        return $this->replace($client,$expected,array_map(fn($id)=>['id'=>$id],$ids),false);
    }
    public function watchlist(string $client,int $expected,array $items): int
    {
        return $this->replace($client,$expected,$items,true);
    }
    public function favorite(string $client,string $station,bool $enabled): int
    {
        \MediaCentre\Validation\Input::uuid($station);
        return $this->db->transaction(function()use($client,$station,$enabled):int{
            $row=$this->db->one('SELECT favorites_revision FROM client_settings WHERE client_id=? FOR UPDATE',[$client]);
            if(!$row)throw new \OutOfBoundsException('Client settings not found.');
            $target=$this->db->one('SELECT enabled FROM radio_stations WHERE id=? FOR SHARE',[$station]);
            if(!$target)throw new \OutOfBoundsException('Station not found.');
            if($enabled && !$target['enabled'])throw new \InvalidArgumentException('Station disabled.');
            $exists=$this->db->one('SELECT station_id FROM client_station_favorites WHERE client_id=? AND station_id=?',[$client,$station])!==null;
            $revision=(int)$row['favorites_revision'];if($exists===$enabled)return $revision;
            if($enabled){
                if((int)$this->db->query('SELECT COUNT(*) FROM client_station_favorites WHERE client_id=?',[$client])->fetchColumn()>=50)throw new \InvalidArgumentException('Favorites limit reached.');
                $order=(int)$this->db->query('SELECT COALESCE(MAX(sort_order)+1,0) FROM client_station_favorites WHERE client_id=?',[$client])->fetchColumn();$this->db->query('INSERT INTO client_station_favorites(client_id,station_id,sort_order) VALUES(?,?,?)',[$client,$station,$order]);
            }
            else $this->db->query('DELETE FROM client_station_favorites WHERE client_id=? AND station_id=?',[$client,$station]);
            $this->db->query('UPDATE client_settings SET favorites_revision=favorites_revision+1 WHERE client_id=?',[$client]);
            $this->db->audit('replace','favorites',$client,['items'],$revision,$revision+1);return $revision+1;
        });
    }
    private function replace(string $client,int $expected,array $items,bool $finance): int
    {
        \MediaCentre\Validation\Input::revision($expected);
        if(!array_is_list($items) || count($items)>50)throw new \InvalidArgumentException('Expected bounded ordered list.');
        $ids=[];
        foreach($items as $item){
            if(!is_array($item) || array_diff(array_keys($item),$finance?['id','show_on_dashboard']:['id']) || !is_string($item['id'] ?? null) || !preg_match('/^[a-f0-9-]{36}$/D',$item['id']))throw new \InvalidArgumentException('Invalid list item.');
            if(isset($ids[$item['id']]))throw new \InvalidArgumentException('Duplicate list item.');
            if($finance && !is_bool($item['show_on_dashboard'] ?? null))throw new \InvalidArgumentException('Dashboard flag required.');
            $ids[$item['id']]=true;
        }
        $table=$finance?'client_finance_watchlist':'client_station_favorites';
        $target=$finance?'finance_instruments':'radio_stations';
        $column=$finance?'instrument_id':'station_id';
        $revision=$finance?'finance_list_revision':'favorites_revision';
        return $this->db->transaction(function()use($client,$expected,$items,$finance,$table,$target,$column,$revision):int{
            $this->db->lockSettings($client,$expected,$revision);
            // Validate before deleting; FK still protects against a concurrent removal.
            foreach($items as $item)if(!$this->db->one("SELECT id FROM $target WHERE id=? FOR SHARE",[$item['id']]))throw new \InvalidArgumentException('Unknown list item.');
            $this->db->query("DELETE FROM $table WHERE client_id=?",[$client]);
            foreach($items as $order=>$item){
                $sql="INSERT INTO $table(client_id,$column,sort_order".($finance?',show_on_dashboard':'').') VALUES(?,?,?'.($finance?',?':'').')';
                $values=[$client,$item['id'],$order];if($finance)$values[]=(int)$item['show_on_dashboard'];
                $this->db->query($sql,$values);
            }
            $this->db->query("UPDATE client_settings SET $revision=$revision+1 WHERE client_id=?",[$client]);
            $this->db->audit('replace',$finance?'watchlist':'favorites',$client,['items'],$expected,$expected+1);
            return $expected+1;
        });
    }
    public function readFavorites(string $client): array {return $this->db->all('SELECT station_id,sort_order FROM client_station_favorites WHERE client_id=? ORDER BY sort_order',[$client]);}
    public function readWatchlist(string $client): array {return $this->db->all('SELECT instrument_id,sort_order,show_on_dashboard FROM client_finance_watchlist WHERE client_id=? ORDER BY sort_order',[$client]);}
}
