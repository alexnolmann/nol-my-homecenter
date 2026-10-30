<?php
declare(strict_types=1);
namespace MediaCentre\Music;
use MediaCentre\Storage\Database;
use MediaCentre\Validation\Input;
use MediaCentre\Shared\Uuid;
final class MusicRepository
{
    public function __construct(private Database $db){}
    public function libraries(bool $admin=false): array
    {
        return $this->db->transaction(function()use($admin){
            $rows=$this->db->all('SELECT * FROM music_libraries'.($admin?'':' WHERE enabled=1').' ORDER BY name,id');
            foreach($rows as &$row){
                $row['tracks']=(int)$this->db->query("SELECT COUNT(*) FROM music_entries WHERE library_id=? AND generation=? AND kind='track'",[$row['id'],$row['generation']])->fetchColumn();
                foreach(['revision','generation','scanned_files','skipped_files'] as $key)$row[$key]=(int)$row[$key];
                $row['enabled']=(bool)$row['enabled'];unset($row['scan_state']);
                if(!$admin)unset($row['root_path'],$row['scan_generation'],$row['requested_at'],$row['updated_at']);
            }return $rows;
        });
    }
    public function save(?string $id,int $revision,string $name,string $path,bool $enabled): void
    {
        $name=trim($name);if($name==='' || mb_strlen($name,'UTF-8')>80)throw new \InvalidArgumentException('Название: от 1 до 80 символов.');
        $requested=trim($path);$existing=$id===null?null:$this->db->one('SELECT root_path FROM music_libraries WHERE id=?',[$id]);
        $root=!$enabled&&$existing&&$requested===$existing['root_path']?$existing['root_path']:MusicFiles::root($requested);
        $this->db->transaction(function()use($id,$revision,$name,$root,$enabled){
            if($id!==null){
                Input::uuid($id);$row=$this->db->one('SELECT * FROM music_libraries WHERE id=? FOR UPDATE',[$id]);
                if(!$row || (int)$row['revision']!==$revision)throw new \MediaCentre\Storage\RevisionConflict();
                $changed=$root!==$row['root_path'];
                $this->db->query("UPDATE music_libraries SET name=?,root_path=?,enabled=?,revision=revision+1,scan_status='queued',scan_state=NULL,error_code=NULL,requested_at=UTC_TIMESTAMP(3),generation=? WHERE id=?",[$name,$root,(int)$enabled,$changed?0:$row['generation'],$id]);
                if($changed)$this->db->query('DELETE FROM music_entries WHERE library_id=?',[$id]);
                $this->db->audit('update','music',$id,['name','settings','enabled'],$revision,$revision+1);
            }else{
                $this->db->one('SELECT server_id FROM app_state WHERE id=1 FOR UPDATE');
                if((int)$this->db->query('SELECT COUNT(*) FROM music_libraries')->fetchColumn()>=32)throw new \InvalidArgumentException('Можно подключить до 32 библиотек.');
                $id=Uuid::random();$this->db->query('INSERT INTO music_libraries(id,name,root_path,enabled) VALUES(?,?,?,?)',[$id,$name,$root,(int)$enabled]);$this->db->audit('create','music',$id,['name','settings','enabled'],null,1);
            }
        });
    }
    public function rescan(string $id,int $revision): void
    {
        Input::uuid($id);$this->db->transaction(function()use($id,$revision){
            $row=$this->db->one('SELECT revision FROM music_libraries WHERE id=? FOR UPDATE',[$id]);
            if(!$row || (int)$row['revision']!==$revision)throw new \MediaCentre\Storage\RevisionConflict();
            $this->db->query("UPDATE music_libraries SET revision=revision+1,scan_status='queued',scan_state=NULL,error_code=NULL,requested_at=UTC_TIMESTAMP(3) WHERE id=?",[$id]);
            $this->db->audit('update','music',$id,['settings'],$revision,$revision+1);
        });
    }
    public function browse(array $query): array
    {
        Input::keys($query,['library','folder','generation','limit','offset','recursive']);
        $library=Input::uuid($query['library'] ?? null);$folder=isset($query['folder'])?Input::uuid($query['folder']):null;
        $limit=filter_var($query['limit'] ?? '50',FILTER_VALIDATE_INT,['options'=>['min_range'=>1,'max_range'=>50]]);
        $offset=filter_var($query['offset'] ?? '0',FILTER_VALIDATE_INT,['options'=>['min_range'=>0,'max_range'=>500000]]);
        $gen=isset($query['generation'])?filter_var($query['generation'],FILTER_VALIDATE_INT,['options'=>['min_range'=>1]]):null;
        $recursive=$query['recursive'] ?? '0';if($limit===false||$offset===false||$gen===false||!in_array($recursive,['0','1'],true))throw new \InvalidArgumentException();
        return $this->db->transaction(function()use($library,$folder,$limit,$offset,$gen,$recursive){
            $lib=$this->db->one('SELECT id,name,generation,enabled FROM music_libraries WHERE id=?',[$library]);
            if(!$lib || !$lib['enabled'])throw new \OutOfBoundsException();
            $generation=$gen ?? (int)$lib['generation'];$rootId=MusicFiles::id($library,'');$folder=$folder ?? $rootId;
            $entry=$this->db->one("SELECT * FROM music_entries WHERE library_id=? AND generation=? AND id=? AND kind='folder'",[$library,$generation,$folder]);
            if(!$entry){if($generation===0)return ['library'=>$library,'generation'=>0,'folder'=>$rootId,'parent'=>null,'name'=>$lib['name'],'folders'=>[],'folder_total'=>0,'items'=>[],'total'=>0,'offset'=>$offset,'limit'=>$limit];throw new \OutOfBoundsException();}
            $where='library_id=? AND generation=?';$args=[$library,$generation];
            if($recursive==='1'){$prefix=str_replace(['!','%','_'],['!!','!%','!_'],$entry['relative_path']);$where.=" AND relative_path LIKE ? ESCAPE '!'";$args[]=($prefix===''?'':$prefix.'/').'%';}
            else{$where.=' AND parent_id=?';$args[]=$folder;}
            $total=(int)$this->db->query("SELECT COUNT(*) FROM music_entries WHERE $where AND kind='track'",$args)->fetchColumn();
            $items=$this->db->all("SELECT id,name,artist,album,bytes FROM music_entries WHERE $where AND kind='track' ORDER BY relative_path,id LIMIT $limit OFFSET $offset",$args);
            foreach($items as &$item){$item['bytes']=(int)$item['bytes'];$item['url']='/api/v1/music/stream/'.$library.'/'.$generation.'/'.$item['id'];}
            $folderTotal=(int)$this->db->query("SELECT COUNT(*) FROM music_entries WHERE library_id=? AND generation=? AND parent_id=? AND kind='folder'",[$library,$generation,$folder])->fetchColumn();
            $folders=$this->db->all("SELECT id,name FROM music_entries WHERE library_id=? AND generation=? AND parent_id=? AND kind='folder' ORDER BY name,id LIMIT $limit OFFSET $offset",[$library,$generation,$folder]);
            return ['library'=>$library,'generation'=>$generation,'folder'=>$folder,'parent'=>$entry['parent_id'],'name'=>$entry['relative_path']===''?$lib['name']:$entry['name'],'folders'=>$folders,'folder_total'=>$folderTotal,'items'=>$items,'total'=>$total,'offset'=>$offset,'limit'=>$limit];
        });
    }
    public function stream(string $library,string $generation,string $id): array
    {
        Input::uuid($library);Input::uuid($id);if(!preg_match('/^[1-9][0-9]{0,17}$/D',$generation))throw new \InvalidArgumentException();
        return $this->db->transaction(function()use($library,$generation,$id){
            $row=$this->db->one("SELECT e.*,l.root_path FROM music_entries e JOIN music_libraries l ON l.id=e.library_id WHERE e.library_id=? AND e.generation=? AND e.id=? AND e.kind='track' AND l.enabled=1",[$library,$generation,$id]);
            if(!$row)throw new \OutOfBoundsException();
            $path=MusicFiles::resolve($row['root_path'],$row['relative_path']);clearstatcache();
            if(!$path || !is_file($path) || !is_readable($path) || strtolower(pathinfo($path,PATHINFO_EXTENSION))!=='mp3' || filesize($path)!==(int)$row['bytes'] || filemtime($path)!==(int)$row['modified'])throw new \OutOfBoundsException();
            return ['status'=>200,'headers'=>['Content-Type'=>'audio/mpeg','Cache-Control'=>'private, no-store','X-Accel-Redirect'=>MusicFiles::internalUri($path)],'body'=>null];
        });
    }
}
