<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
$root=dirname(__DIR__,2);$db=new MediaCentre\Storage\Database($pdo);
$dir=$root.'/server/storage/photoframe';if(!is_dir($dir))mkdir($dir,0700,true);
$photos=new MediaCentre\Photoframe\PhotoRepository($db,$dir);
$assert=static function(bool $condition,string $message):void{if(!$condition)throw new RuntimeException($message);};
$upload=['name'=>['one.jpg','two.png'],'type'=>['image/jpeg','image/png'],'tmp_name'=>['first','second'],'error'=>[0,0],'size'=>[123,456]];
$batch=MediaCentre\Photoframe\PhotoRepository::batch($upload);
$assert(count($batch)===2 && $batch[1]['name']==='two.png' && $batch[1]['size']===456,'Multi-file upload was not normalized.');
try{$invalid=$upload;$invalid['size']=[123];MediaCentre\Photoframe\PhotoRepository::batch($invalid);throw new RuntimeException('Malformed upload list accepted.');}catch(InvalidArgumentException){}
try{$invalid=$upload;foreach($invalid as $key=>$values)$invalid[$key]=array_fill(0,11,$values[0]);MediaCentre\Photoframe\PhotoRepository::batch($invalid);throw new RuntimeException('Oversized upload batch accepted.');}catch(InvalidArgumentException){}
$before=$photos->state()['enabled'];$id=MediaCentre\Shared\Uuid::random();$other=MediaCentre\Shared\Uuid::random();$location=MediaCentre\Shared\Uuid::random();$client=MediaCentre\Shared\Uuid::random();$token=bin2hex(random_bytes(32));$file=null;$otherFile=null;
try{
    $photos->toggle(false);
    $image=imagecreatetruecolor(64,40);imagefill($image,0,0,imagecolorallocate($image,12,72,110));ob_start();imagejpeg($image,null,80);$bytes=ob_get_clean();imagedestroy($image);
    $hash=hash('sha256',$bytes);$file=$dir.'/'.$id.'-full-'.$hash.'.jpg';file_put_contents($file,$bytes);
    $pdo->prepare('INSERT INTO photoframe_photos(id,sort_order,full_hash,thumb_hash,width,height,bytes) VALUES(?,999999,?,?,?,?,?)')->execute([$id,$hash,$hash,64,40,strlen($bytes)]);
    $otherFile=$dir.'/'.$other.'-full-'.$hash.'.jpg';file_put_contents($otherFile,$bytes);
    $pdo->prepare('INSERT INTO photoframe_photos(id,sort_order,full_hash,thumb_hash,width,height,bytes) VALUES(?,1000000,?,?,?,?,?)')->execute([$other,$hash,$hash,64,40,strlen($bytes)]);
    $photos->move($other,-1);$ordered=array_column($photos->state()['items'],'id');$assert(array_search($other,$ordered,true)<array_search($id,$ordered,true),'Photo reorder failed.');
    (new MediaCentre\Weather\LocationRepository($db))->create($location,'Photo test','59.938630','30.314130','Europe/Moscow');
    (new MediaCentre\Client\ClientRepository($db))->create($client,'Photo test',$location,['photoframe:read']);
    $pdo->prepare('UPDATE clients SET token_hash=? WHERE id=?')->execute([hash('sha256',$token,true),$client]);
    $api=new MediaCentre\Http\Api($config,$root.'/server');$auth=['authorization'=>'Bearer '.$token];
    $path='/api/v1/plugins/photoframe/manifest';$reply=$api->handle('GET',$path,[],$auth,'');$assert($reply['status']===404,'Disabled album was visible.');
    $photos->toggle(true);$reply=$api->handle('GET',$path,[],$auth,'');$assert($reply['status']===200,'Manifest unavailable.');
    $listed=array_column($reply['body']['data']['items'],'id');$assert(in_array($id,$listed,true),'Photo missing from manifest.');
    $same=$api->handle('GET',$path,[],$auth+['if-none-match'=>$reply['headers']['ETag']],'');$assert($same['status']===304,'Manifest ETag unstable.');
    $imagePath='/api/v1/plugins/photoframe/photos/'.$id.'/'.$hash;
    $result=$api->handle('GET',$imagePath,[],$auth,'');$assert($result['status']===200 && $result['file']===$file,'Image not protected/served.');
    $assert($api->handle('GET',$imagePath,[],$auth+['if-none-match'=>$result['headers']['ETag']],'')['status']===304,'Photo ETag failed.');
    $assert($api->handle('GET','/api/v1/plugins/photoframe/photos/'.$id.'/'.str_repeat('0',64),[],$auth,'')['status']===404,'Wrong photo hash accepted.');
    $assert($api->handle('GET',$imagePath,[],[],'')['status']===401,'Image available without token.');
    $pdo->prepare('UPDATE clients SET scopes=? WHERE id=?')->execute(['[]',$client]);
    $assert($api->handle('GET',$imagePath,[],$auth,'')['status']===403,'Image available without scope.');
    $pdo->prepare('UPDATE clients SET scopes=? WHERE id=?')->execute([MediaCentre\Storage\Database::json(['photoframe:read']),$client]);
    $photos->delete($id);$file=null;$photos->delete($other);$otherFile=null;$assert($api->handle('GET',$imagePath,[],$auth,'')['status']===404,'Deleted photo still available.');
    echo "PASS: photoframe gate, manifest/ETag, protected image and deletion.\n";
}finally{
    if($file!==null && is_file($file))@unlink($file);
    if($otherFile!==null && is_file($otherFile))@unlink($otherFile);
    $pdo->prepare('DELETE FROM photoframe_photos WHERE id IN (?,?)')->execute([$id,$other]);
    $photos->toggle($before);
}
