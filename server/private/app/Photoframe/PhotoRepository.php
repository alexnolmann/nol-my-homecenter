<?php
declare(strict_types=1);
namespace MediaCentre\Photoframe;

use MediaCentre\Shared\Uuid;
use MediaCentre\Storage\Database;

/** One server-managed album. Only normalized JPEG variants are retained. */
final class PhotoRepository
{
    public const MAX_FILES=10;
    public const MAX_BYTES=24*1024*1024;
    public const MAX_PIXELS=48000000;
    public function __construct(private Database $db, private string $directory){}
    /** Turn PHP's photos[] upload structure into individually validated files. */
    public static function batch(array $upload): array
    {
        $fields=['name','type','tmp_name','error','size'];
        foreach($fields as $field)if(!isset($upload[$field])||!is_array($upload[$field]))throw new \InvalidArgumentException('Выберите фотографии для загрузки.');
        $count=count($upload['name']);
        if($count<1||$count>self::MAX_FILES)throw new \InvalidArgumentException('За один раз можно загрузить от 1 до '.self::MAX_FILES.' фотографий.');
        $files=[];
        foreach($fields as $field){if(array_keys($upload[$field])!==range(0,$count-1))throw new \InvalidArgumentException('Некорректный список файлов.');}
        for($index=0;$index<$count;$index++){
            $file=[];
            foreach($fields as $field){if(is_array($upload[$field][$index]))throw new \InvalidArgumentException('Некорректный список файлов.');$file[$field]=$upload[$field][$index];}
            $files[]=$file;
        }
        return $files;
    }
    public function state(): array
    {
        return $this->db->transaction(function(): array {
            $state=$this->db->one('SELECT enabled,revision FROM photoframe_settings WHERE id=1');
            return ['enabled'=>(bool)($state['enabled'] ?? false),'revision'=>(int)($state['revision'] ?? 1),
                'items'=>$this->db->all('SELECT id,sort_order,full_hash,thumb_hash,width,height,bytes FROM photoframe_photos ORDER BY sort_order,id')];
        });
    }
    public function enabled(): bool {return (bool)($this->db->one('SELECT enabled FROM photoframe_settings WHERE id=1')['enabled'] ?? false);}
    private function bump(): void
    {
        $this->db->query('INSERT INTO photoframe_settings(id,enabled,revision) VALUES(1,FALSE,1) ON DUPLICATE KEY UPDATE revision=revision+1');
    }
    public function toggle(bool $enabled): void
    {
        $this->db->transaction(function()use($enabled):void {
            $state=$this->db->one('SELECT enabled,revision FROM photoframe_settings WHERE id=1 FOR UPDATE');
            if(!$state){$this->db->query('INSERT INTO photoframe_settings(id,enabled,revision) VALUES(1,?,1)',[(int)$enabled]);$old=0;$new=1;}
            else {if((bool)$state['enabled']===$enabled)return;$old=(int)$state['revision'];$new=$old+1;$this->db->query('UPDATE photoframe_settings SET enabled=?,revision=? WHERE id=1',[(int)$enabled,$new]);}
            $this->db->audit('update','photoframe','00000000-0000-4000-8000-000000000001',['enabled'],$old,$new);
        });
    }
    public function add(array $upload): void
    {
        if(in_array($upload['error'] ?? null,[UPLOAD_ERR_INI_SIZE,UPLOAD_ERR_FORM_SIZE],true))throw new \InvalidArgumentException('Фото должно быть не больше 24 МБ.');
        if(($upload['error'] ?? UPLOAD_ERR_NO_FILE)!==UPLOAD_ERR_OK || !is_uploaded_file($upload['tmp_name'] ?? ''))throw new \InvalidArgumentException('Не удалось принять файл.');
        $file=$upload['tmp_name'];$size=(int)($upload['size'] ?? 0);
        if($size<1 || $size>self::MAX_BYTES)throw new \InvalidArgumentException('Фото должно быть не больше 24 МБ.');
        $info=@getimagesize($file);$w=(int)($info[0] ?? 0);$h=(int)($info[1] ?? 0);$mime=$info['mime'] ?? '';
        if(!in_array($mime,['image/jpeg','image/png'],true)||$w<1||$h<1||$w*$h>self::MAX_PIXELS)throw new \InvalidArgumentException('Нужен JPEG/PNG не больше 48 Мп.');
        $source=$mime==='image/jpeg'?@imagecreatefromjpeg($file):@imagecreatefrompng($file);
        if(!$source)throw new \InvalidArgumentException('Повреждённое изображение.');
        $full=null;$thumb=null;$paths=[];
        try {
            if($mime==='image/jpeg' && function_exists('exif_read_data')){
                $exif=@exif_read_data($file);$orientation=(int)($exif['Orientation'] ?? 1);
                if(in_array($orientation,[2,5,7],true))imageflip($source,IMG_FLIP_HORIZONTAL);
                elseif($orientation===4)imageflip($source,IMG_FLIP_VERTICAL);
                $angle=match($orientation){3=>180,5,6=>-90,7,8=>90,default=>0};
                if($angle!==0){$rotated=imagerotate($source,$angle,0);if(!$rotated)throw new \InvalidArgumentException('Не удалось повернуть фото.');imagedestroy($source);$source=$rotated;}
            }
            $w=imagesx($source);$h=imagesy($source);
            $full=$this->resize($source,1920,1200);$thumb=$this->resize($source,360,240);
            $id=Uuid::random();$variants=[];
            foreach(['full'=>$full,'thumb'=>$thumb] as $kind=>$image){
                ob_start();imagejpeg($image,null,$kind==='full'?85:78);$bytes=ob_get_clean();
                if(!is_string($bytes)||$bytes==='')throw new \RuntimeException('Не удалось подготовить фото.');
                $hash=hash('sha256',$bytes);$variants[$kind]=['hash'=>$hash,'bytes'=>strlen($bytes),'width'=>imagesx($image),'height'=>imagesy($image)];
                if(!is_dir($this->directory)&&!mkdir($this->directory,0700,true)&&!is_dir($this->directory))throw new \RuntimeException('Хранилище недоступно.');
                $path=$this->path($id,$hash,$kind);$paths[]=$path;
                if(file_put_contents($path,$bytes,LOCK_EX)!==strlen($bytes))throw new \RuntimeException('Не удалось сохранить фото.');
            }
            $this->db->transaction(function()use($id,$variants):void {
                $this->db->query('INSERT IGNORE INTO photoframe_settings(id,enabled,revision) VALUES(1,FALSE,1)');
                $this->db->one('SELECT revision FROM photoframe_settings WHERE id=1 FOR UPDATE');
                $count=(int)$this->db->query('SELECT COUNT(*) FROM photoframe_photos')->fetchColumn();
                if($count>=500)throw new \InvalidArgumentException('Достигнут лимит 500 фотографий.');
                $order=(int)$this->db->query('SELECT COALESCE(MAX(sort_order),0)+1 FROM photoframe_photos')->fetchColumn();
                $this->db->query('INSERT INTO photoframe_photos(id,sort_order,full_hash,thumb_hash,width,height,bytes) VALUES(?,?,?,?,?,?,?)',[$id,$order,$variants['full']['hash'],$variants['thumb']['hash'],$variants['full']['width'],$variants['full']['height'],$variants['full']['bytes']]);
                $this->bump();$this->db->audit('create','photoframe',$id,['items'],null,1);
            });
        }catch(\Throwable $error){foreach($paths as $path)@unlink($path);throw $error;}
        finally{imagedestroy($source);if($full)imagedestroy($full);if($thumb)imagedestroy($thumb);}
    }
    private function resize(\GdImage $source,int $maxW,int $maxH): \GdImage
    {
        $scale=min(1,$maxW/imagesx($source),$maxH/imagesy($source));$w=max(1,(int)round(imagesx($source)*$scale));$h=max(1,(int)round(imagesy($source)*$scale));
        $out=imagecreatetruecolor($w,$h);imagefill($out,0,0,imagecolorallocate($out,18,22,29));
        if(!imagecopyresampled($out,$source,0,0,0,0,$w,$h,imagesx($source),imagesy($source)))throw new \RuntimeException('Не удалось уменьшить фото.');
        return $out;
    }
    public function delete(string $id): void
    {
        $row=$this->db->transaction(function()use($id):?array {
            $row=$this->db->one('SELECT * FROM photoframe_photos WHERE id=? FOR UPDATE',[$id]);if(!$row)return null;
            $this->db->query('DELETE FROM photoframe_photos WHERE id=?',[$id]);$this->bump();$this->db->audit('delete','photoframe',$id,['items'],1,2);return $row;
        });
        if($row)foreach(['full','thumb'] as $kind)@unlink($this->path($id,$row[$kind.'_hash'],$kind));
    }
    public function move(string $id,int $direction): void
    {
        $this->db->transaction(function()use($id,$direction):void {
            $rows=$this->db->all('SELECT id FROM photoframe_photos ORDER BY sort_order,id FOR UPDATE');$ids=array_column($rows,'id');$index=array_search($id,$ids,true);if($index===false)throw new \OutOfBoundsException();
            $other=$index+$direction;if($other<0||$other>=count($ids))return;[$ids[$index],$ids[$other]]=[$ids[$other],$ids[$index]];
            foreach($ids as $order=>$photo)$this->db->query('UPDATE photoframe_photos SET sort_order=? WHERE id=?',[$order+1,$photo]);
            $this->bump();$this->db->audit('update','photoframe',$id,['items'],1,2);
        });
    }
    public function image(string $id,string $hash,string $kind): ?array
    {
        $row=$this->db->transaction(fn()=> $this->db->one('SELECT full_hash,thumb_hash FROM photoframe_photos WHERE id=?',[$id]));
        if(!$row || !hash_equals($row[$kind.'_hash'],$hash))return null;
        $path=$this->path($id,$hash,$kind);if(!is_file($path))return null;
        return ['path'=>$path,'bytes'=>filesize($path)];
    }
    private function path(string $id,string $hash,string $kind): string {return $this->directory.'/'.$id.'-'.$kind.'-'.$hash.'.jpg';}
}
