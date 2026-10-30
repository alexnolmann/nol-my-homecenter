<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
// h2-bootstrap validates the dedicated *_test schema and never selects the working DB.
$root=dirname(__DIR__,2);
[$config,$pdo]=require $root.'/server/tests/h2-bootstrap.php';
$db=new MediaCentre\Storage\Database($pdo);
$runtime=$root.'/.local/osp-http-web';
if(!is_dir($runtime))mkdir($runtime,0700,true);
if(($argv[1] ?? '')==='env'){
    $dbConfig=$config->values['database'];
    $env=['APP_ENV'=>'production','MC_HTTP_HOST'=>'mediacentre.miniw','DB_HOST'=>$dbConfig['host'],'DB_PORT'=>(string)$dbConfig['port'],'DB_NAME'=>$dbConfig['name'],'DB_USER'=>$dbConfig['user'],'DB_PASSWORD'=>$dbConfig['password']];
    $text='';foreach($env as $key=>$value)$text.='fastcgi_param '.$key.' "'.str_replace(['\\','"','$'],['\\\\','\\"','\\$'],$value).'";'."\n";
    file_put_contents($runtime.'/env.conf',$text);exit;
}
$assert=fn($ok,$message)=>$ok ?: throw new RuntimeException($message);
$uuid=MediaCentre\Shared\Uuid::random(...);
$admin=$uuid();$login='http-'.bin2hex(random_bytes(5));$password=bin2hex(random_bytes(16));
$location=$uuid();$client=$uuid();$token=bin2hex(random_bytes(32));$library=null;$photoIds=[];
$photos=new MediaCentre\Photoframe\PhotoRepository($db,$root.'/server/storage/photoframe');$enabled=$photos->enabled();
$musicRoot=$runtime.'/music-'.bin2hex(random_bytes(4));mkdir($musicRoot);
$mp3="\xff\xfb\x90\x64".str_repeat("\0",2048);file_put_contents($musicRoot.'/01.mp3',$mp3);
$image=imagecreatetruecolor(24,16);imagejpeg($image,$runtime.'/upload.jpg');imagedestroy($image);
$curl=curl_init();
$request=function(string $path,?array $form=null,array $headers=[],string $host='mediacentre.miniw')use($curl):array{
    curl_setopt_array($curl,[CURLOPT_URL=>'http://127.0.0.1:18089'.$path,CURLOPT_RETURNTRANSFER=>true,CURLOPT_HEADER=>true,CURLOPT_FOLLOWLOCATION=>false,CURLOPT_TIMEOUT=>15,CURLOPT_COOKIEFILE=>'',CURLOPT_HTTPHEADER=>array_merge(['Host: '.$host],$headers),CURLOPT_POST=>$form!==null]);
    if($form!==null)curl_setopt($curl,CURLOPT_POSTFIELDS,isset($form['photos[0]'])?$form:http_build_query($form));
    $reply=curl_exec($curl);if($reply===false)throw new RuntimeException('HTTP unavailable: '.curl_error($curl));
    $size=curl_getinfo($curl,CURLINFO_HEADER_SIZE);return ['status'=>curl_getinfo($curl,CURLINFO_RESPONSE_CODE),'headers'=>substr($reply,0,$size),'body'=>substr($reply,$size)];
};
$csrf=function(string $html)use($assert):string{$assert(preg_match('/name="csrf" value="([a-f0-9]+)"/',$html,$m)===1,'CSRF missing');return $m[1];};
try{
    $db->query('INSERT INTO admin_users(id,login,password_hash) VALUES(?,?,?)',[$admin,$login,password_hash($password,PASSWORD_DEFAULT)]);
    (new MediaCentre\Weather\LocationRepository($db))->create($location,'HTTP test','59','30','Europe/Moscow');
    (new MediaCentre\Client\ClientRepository($db))->create($client,'HTTP test',$location,['music:read','photoframe:read','preferences:read']);
    $db->query('UPDATE clients SET token_hash=? WHERE id=?',[hash('sha256',$token,true),$client]);
    $auth=['Authorization: Bearer '.$token];
    $r=$request('/admin/');$assert($r['status']===200,'HTTP login page unavailable');
    $assert(str_contains(strtolower($r['headers']),'httponly') && !preg_match('/;\s*secure\b/i',$r['headers']),'HTTP cookie policy failed');
    $r=$request('/admin/',['action'=>'login','csrf'=>$csrf($r['body']),'login'=>$login,'password'=>$password]);$assert($r['status']===303,'HTTP login failed');
    $r=$request('/admin/');$assert($r['status']===200 && str_contains($r['body'],$login),'HTTP session failed');$nonce=$csrf($r['body']);
    $assert($request('/admin/',['action'=>'logout','csrf'=>'bad'])['status']===403,'HTTP CSRF bypass');
    $assert($request('/admin/',null,[],'evil.example')['status']===403,'Foreign host granted administration');
    foreach(['/storage/','/private/config/local.php','/h0/probe.php','/.user.ini','/assets/h0-admin.css'] as $path)$assert($request($path)['status']===404,'Private route exposed');
    $assert($request('/assets/admin.js')['status']===200,'Admin asset missing');
    $before=array_column($photos->state()['items'],'id');
    $r=$request('/admin/?section=photoframe',['action'=>'photo_upload','csrf'=>$nonce,'photos[0]'=>new CURLFile($runtime.'/upload.jpg','image/jpeg','test.jpg')]);
    $photoIds=array_values(array_diff(array_column($photos->state()['items'],'id'),$before));
    $assert($r['status']===303 && count($photoIds)===1,'HTTP multipart photo upload failed');
    $r=$request('/admin/?section=photoframe',['action'=>'photo_toggle','csrf'=>$nonce,'enabled'=>'1']);$assert($r['status']===303,'Photo enable failed');
    $r=$request('/api/v1/plugins/photoframe/manifest',null,$auth);$assert($r['status']===200,'HTTP photo manifest failed '.$r['status'].' '.$r['body']);
    $photo=$db->one('SELECT * FROM photoframe_photos WHERE id=?',[$photoIds[0]]);$path='/api/v1/plugins/photoframe/photos/'.$photo['id'].'/'.$photo['full_hash'];
    $r=$request($path,null,$auth);$assert($r['status']===200 && str_contains($r['headers'],'image/jpeg') && str_starts_with($r['body'],"\xff\xd8"),'HTTP JPEG failed');
    $assert($request($path)['status']===401,'Anonymous photo exposed');
    $repo=new MediaCentre\Music\MusicRepository($db);$repo->save(null,1,'HTTP test',$musicRoot,true);
    $library=$db->one('SELECT id FROM music_libraries WHERE root_path=?',[str_replace('\\','/',$musicRoot)])['id'];
    $scanner=new MediaCentre\Music\MusicScanner($db);for($i=0;$i<20;$i++){$scanner->tick();if($db->one('SELECT scan_status FROM music_libraries WHERE id=?',[$library])['scan_status']==='ready')break;}
    $listing=$repo->browse(['library'=>$library]);$track=$listing['items'][0];$stream='/api/v1/music/stream/'.$library.'/'.$listing['generation'].'/'.$track['id'];
    $r=$request($stream,null,$auth);$assert($r['status']===200 && $r['body']===$mp3 && str_contains($r['headers'],'audio/mpeg'),'Nginx music delivery failed');
    $r=$request($stream,null,array_merge($auth,['Range: bytes=2-4']));$assert($r['status']===206 && $r['body']===substr($mp3,2,3) && str_contains($r['headers'],'bytes 2-4/2052'),'Nginx Range failed');
    $internal=MediaCentre\Music\MusicFiles::internalUri(realpath($musicRoot.'/01.mp3'));$assert($request($internal)['status']===404,'Internal music path public');
    $assert($request($stream)['status']===401,'Anonymous music exposed');
    $assert($request('/admin/',['action'=>'logout','csrf'=>$nonce])['status']===303,'HTTP logout failed');
    echo "PASS: real Nginx/PHP HTTP login/cookies/CSRF/logout, host gate, private routes, multipart photo upload, authenticated JPEG, MP3/Range and internal-route denial.\n";
}finally{
    curl_close($curl);
    foreach($photoIds as $id)$photos->delete($id);$photos->toggle($enabled);
    if($library!==null){$db->query('DELETE FROM music_entries WHERE library_id=?',[$library]);$db->query('DELETE FROM music_libraries WHERE id=?',[$library]);}
    $db->query('DELETE FROM audit_events WHERE admin_id=? OR client_id=?',[$admin,$client]);
    $db->query('DELETE FROM client_settings WHERE client_id=?',[$client]);$db->query('DELETE FROM clients WHERE id=?',[$client]);
    $db->query('DELETE FROM locations WHERE id=?',[$location]);$db->query('DELETE FROM admin_users WHERE id=?',[$admin]);
    unlink($musicRoot.'/01.mp3');rmdir($musicRoot);unlink($runtime.'/upload.jpg');
}
