<?php
declare(strict_types=1);
namespace MediaCentre\Music;
use MediaCentre\Storage\Database;
/** Checkpoint after bounded batches; publish only complete scans, retaining yesterday's catalogue. */
final class MusicScanner
{
    public function __construct(private Database $db){}
    public function tick(int $budget=250): void
    {
        if(!(int)$this->db->query("SELECT GET_LOCK('mediacentre_music_scan',0)")->fetchColumn())return;
        try{
            $this->db->transaction(function()use($budget){
                $lib=$this->db->one("SELECT * FROM music_libraries WHERE enabled=1 AND (scan_status IN ('queued','scanning') OR (scan_status IN ('ready','failed') AND updated_at<UTC_TIMESTAMP(3)-INTERVAL 1 HOUR)) ORDER BY requested_at,id LIMIT 1 FOR UPDATE");
                if(!$lib)return;
                try{
                    if(MusicFiles::root($lib['root_path'])!==$lib['root_path'])throw new \RuntimeException();
                    if($lib['scan_status']!=='scanning'){
                        $generation=max((int)$lib['scan_generation'],(int)$lib['generation'])+1;
                        $this->db->query('UPDATE music_libraries SET scan_generation=? WHERE id=?',[$generation,$lib['id']]);
                        $this->db->query('DELETE FROM music_entries WHERE library_id=? AND generation=?',[$lib['id'],$generation]);
                        $state=['todo'=>[''],'current'=>null,'last'=>null];$count=0;$skipped=0;
                        $this->db->query('DELETE FROM music_entries WHERE library_id=? AND generation<>? AND indexed_at<UTC_TIMESTAMP(3)-INTERVAL 1 DAY',[$lib['id'],$lib['generation']]);
                    }else{$generation=(int)$lib['scan_generation'];$state=json_decode($lib['scan_state'],true,512,JSON_THROW_ON_ERROR);$count=(int)$lib['scanned_files'];$skipped=(int)$lib['skipped_files'];}
                    $started=microtime(true);$cachedPath=null;$names=[];
                    for($n=0;$n<$budget && microtime(true)-$started<2;$n++){
                        if($state['current']===null){
                            if(!$state['todo'])break;
                            $relative=array_pop($state['todo']);$directory=MusicFiles::resolve($lib['root_path'],$relative);
                            if(!$directory || !is_dir($directory) || !is_readable($directory))throw new \RuntimeException();
                            $this->entry($lib['id'],$generation,$relative,'folder',['name'=>$relative===''?$lib['name']:basename($relative),'artist'=>null,'album'=>null],0,0);
                            $state['current']=$relative;$state['last']=null;
                        }
                        $directory=MusicFiles::resolve($lib['root_path'],$state['current']);if(!$directory)throw new \RuntimeException();
                        if($cachedPath!==$directory){$names=@scandir($directory);if($names===false || count($names)>50000)throw new \RuntimeException();$names=array_values(array_filter($names,static fn($name)=>$name!=='.'&&$name!=='..'));$cachedPath=$directory;}
                        $low=0;$high=count($names);while($low<$high){$mid=intdiv($low+$high,2);if($state['last']===null||strcmp($names[$mid],$state['last'])>0)$high=$mid;else $low=$mid+1;}$next=$names[$low] ?? null;
                        if($next===null){$state['current']=null;$state['last']=null;continue;}
                        $state['last']=$next;$relative=($state['current']===''?'':$state['current'].'/').$next;
                        if(strlen($relative)>2048 || count($state['todo'])>10000)throw new \RuntimeException();
                        $candidate=$directory.'/'.$next;
                        // Do not traverse symlinks/junctions or leave the configured root.
                        $path=MusicFiles::resolve($lib['root_path'],$relative);
                        if(!$path || is_link($candidate)){$skipped++;continue;}
                        if(is_dir($path)){
                            if(str_replace('\\','/',$candidate)!==$path){$skipped++;continue;}
                            if(count($state['todo'])>=10000)throw new \LengthException();$state['todo'][]=$relative;
                        }elseif(is_file($path)&&strtolower(pathinfo($path,PATHINFO_EXTENSION))==='mp3'){
                            if($count>=100000)throw new \LengthException();$bytes=(int)filesize($path);$mtime=(int)filemtime($path);
                            $old=$this->db->one("SELECT name,artist,album,bytes,modified FROM music_entries WHERE library_id=? AND generation=? AND id=? AND kind='track'",[$lib['id'],$lib['generation'],MusicFiles::id($lib['id'],$relative)]);
                            $tags=$old&&(int)$old['bytes']===$bytes&&(int)$old['modified']===$mtime?$old:MusicFiles::metadata($path);if($tags===null){$skipped++;continue;}
                            $this->entry($lib['id'],$generation,$relative,'track',$tags,$bytes,$mtime);$count++;
                        }
                    }
                    $done=$state['current']===null&&!$state['todo'];
                    $this->db->query('UPDATE music_libraries SET scan_generation=?,scan_state=?,scan_status=?,scanned_files=?,skipped_files=?,error_code=NULL'.($done?',generation=?,scanned_at=UTC_TIMESTAMP(3)':'').' WHERE id=?',array_merge([$generation,$done?null:Database::json($state),$done?'ready':'scanning',$count,$skipped],$done?[$generation]:[],[$lib['id']]));
                }catch(\Throwable){
                    if(isset($generation)&&$generation!==(int)$lib['generation'])$this->db->query('DELETE FROM music_entries WHERE library_id=? AND generation=?',[$lib['id'],$generation]);
                    $this->db->query("UPDATE music_libraries SET scan_status='failed',scan_state=NULL,error_code='DIRECTORY_UNAVAILABLE' WHERE id=?",[$lib['id']]);
                }
            });
        }finally{$this->db->query("SELECT RELEASE_LOCK('mediacentre_music_scan')");}
    }
    private function entry(string $library,int $generation,string $relative,string $kind,array $tags,int $bytes,int $mtime): void
    {
        $id=MusicFiles::id($library,$relative);$parent=$relative===''?null:MusicFiles::id($library,str_contains($relative,'/')?substr($relative,0,strrpos($relative,'/')):'');
        $this->db->query('INSERT INTO music_entries(library_id,generation,id,parent_id,kind,relative_path,name,artist,album,bytes,modified) VALUES(?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),artist=VALUES(artist),album=VALUES(album),bytes=VALUES(bytes),modified=VALUES(modified)',[$library,$generation,$id,$parent,$kind,$relative,$tags['name'],$tags['artist'],$tags['album'],$bytes,$mtime]);
    }
}
