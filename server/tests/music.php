<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
$db=new MediaCentre\Storage\Database($pdo);
$assert=fn($ok,$message)=>$ok ?: throw new RuntimeException($message);
$root=dirname(__DIR__,2).'/.local/music-tests/'.bin2hex(random_bytes(8));
mkdir($root.'/Альбом',0700,true);
file_put_contents($root.'/Альбом/01.mp3',"\xff\xfb\x90\x64".str_repeat("\0",2048));
file_put_contents($root.'/bad.mp3','not mp3');
file_put_contents($root.'/secret.txt','private');
$text="\x03Трек <&>";$frame='TIT2'.pack('N',strlen($text))."\0\0".$text;$length=strlen($frame);
file_put_contents($root.'/tags.tmp',"ID3\x03\0\0".pack('C4',($length>>21)&127,($length>>14)&127,($length>>7)&127,$length&127).$frame."\xff\xfb\x90\x64");
$assert(MediaCentre\Music\MusicFiles::metadata($root.'/tags.tmp')['name']==='Трек <&>','Bounded UTF-8 ID3 metadata failed');
$repo=new MediaCentre\Music\MusicRepository($db);$library=null;$client=null;
try{
 $repo->save(null,1,'<b>Music test</b>',$root,true);
 $library=$db->one('SELECT id FROM music_libraries WHERE root_path=?',[str_replace('\\','/',$root)])['id'];
 $scanner=new MediaCentre\Music\MusicScanner($db);
 for($i=0;$i<40;$i++){$scanner->tick(2);$lib=$db->one('SELECT * FROM music_libraries WHERE id=?',[$library]);if($lib['scan_status']==='ready')break;}
 $assert($lib['scan_status']==='ready'&&(int)$lib['scanned_files']===1,'Bounded scan/MP3 filter failed');
 $listing=$repo->browse(['library'=>$library]);$assert(count($listing['folders'])===1 && $listing['total']===0,'Tree root failed');
 $folder=$listing['folders'][0]['id'];$tracks=$repo->browse(['library'=>$library,'folder'=>$folder]);
 $assert($tracks['total']===1 && $tracks['items'][0]['name']==='01','Folder track failed');
 $recursive=$repo->browse(['library'=>$library,'recursive'=>'1']);$assert($recursive['total']===1,'Recursive folder failed');
 $track=$tracks['items'][0];$stream=$repo->stream($library,(string)$listing['generation'],$track['id']);
 $assert($stream['status']===200 && str_contains($stream['headers']['X-Accel-Redirect'],'%D0%90'),'Protected internal path encoding failed');
 $assert(MediaCentre\Music\MusicFiles::resolve(MediaCentre\Music\MusicFiles::root($root.'/Альбом'),'../secret.txt')===null,'Existing file outside root exposed');
 $assert(MediaCentre\Music\MusicFiles::resolve($root,'/outside')===null,'Absolute file path');
 $old=$lib['generation'];$repo->rescan($library,1);$scanner->tick(1);
 $assert($repo->browse(['library'=>$library])['generation']===(int)$old,'Partial scan exposed');
 try{$repo->rescan($library,1);throw new RuntimeException('CAS bypass');}catch(MediaCentre\Storage\RevisionConflict){}
 $location=$db->one('SELECT id FROM locations LIMIT 1')['id'];$client=MediaCentre\Shared\Uuid::random();$token=bin2hex(random_bytes(32));
 (new MediaCentre\Client\ClientRepository($db))->create($client,'Music test',$location,['music:read']);
 $db->query('UPDATE clients SET token_hash=? WHERE id=?',[hash('sha256',$token,true),$client]);
 $api=new MediaCentre\Http\Api($config,dirname(__DIR__));$auth=['authorization'=>'Bearer '.$token];
 $call=fn($path,$query=[],$headers=[])=>$api->handle('GET','/api/v1/music/'.$path,$query,$headers+$auth,'');
 $public=$call('libraries');$assert($public['status']===200&&!str_contains(json_encode($public['body'],JSON_UNESCAPED_SLASHES),str_replace('\\','/',$root)),'Root leaked');
 foreach($public['body']['data']['items'] as $item)$assert(!array_key_exists('root_path',$item)&&!array_key_exists('scan_state',$item)&&!array_key_exists('scan_generation',$item),'Private library fields leaked');
 $assert($call('browse',['library'=>$library])['status']===200,'Browse response contract failed');
 $page=$call('browse',['library'=>$library,'limit'=>'1','offset'=>'1']);$assert($page['status']===200&&$page['body']['data']['folders']===[]&&$page['body']['data']['folder_total']===1,'Folder pagination failed');
 $assert($call('browse',['library'=>$library,'limit'=>'51'])['status']===400,'Metadata response bound failed');
 $assert($call('browse',['library'=>$library,'limit'=>'101'])['status']===400,'Unbounded API');
 $assert($call('browse',['library'=>$library,'path'=>'../'])['status']===400,'Foreign path accepted');
 $assert($call('browse',['library'=>$library],['x-mediacentre-server-id'=>MediaCentre\Shared\Uuid::random()])['status']===401,'Server identity bypass');
 $assert($call('stream/'.$library.'/'.$old.'/'.$track['id'])['status']===200,'Stream auth failed');
 $db->query('UPDATE clients SET scopes=? WHERE id=?',['["preferences:read"]',$client]);$assert($call('libraries')['status']===403,'Scope bypass');
 $db->query('UPDATE clients SET revoked_at=UTC_TIMESTAMP(3) WHERE id=?',[$client]);$assert($call('libraries')['status']===401,'Revocation bypass');
 file_put_contents($root.'/Альбом/01.mp3',"\xff\xfbchanged");
 try{$repo->stream($library,(string)$old,$track['id']);throw new RuntimeException('Changed file served');}catch(OutOfBoundsException){}
 $db->query("UPDATE music_libraries SET root_path=?,scan_status='queued' WHERE id=?",[$root.'/missing',$library]);$scanner->tick();
 $assert((int)$db->one('SELECT generation FROM music_libraries WHERE id=?',[$library])['generation']===(int)$old,'Failed scan erased catalogue');
 $repo->save($library,2,'<b>Music test</b>',$root.'/missing',false);
 $assert(!$db->one('SELECT enabled FROM music_libraries WHERE id=?',[$library])['enabled'],'Unavailable library could not be disabled');
 $admin=['login'=>'test'];$section='music';$message='';$csrf='test';$view=(new MediaCentre\Admin\FormActions($db))->view();$e=MediaCentre\Admin\Pages::escape(...);
 ob_start();require dirname(__DIR__).'/private/views/admin/panel.php';$html=ob_get_clean();
 $assert(str_contains($html,'&lt;b&gt;Music test&lt;/b&gt;') && !str_contains($html,'<b>Music test</b>') && str_contains($html,'name="root_path"'),'Admin escaping/form missing');
 $repo->save($library,3,'<b>Music test</b>',$root.'/Альбом',true);
 $assert((int)$db->query('SELECT COUNT(*) FROM music_entries WHERE library_id=?',[$library])->fetchColumn()===0&&(int)$db->one('SELECT generation FROM music_libraries WHERE id=?',[$library])['generation']===0,'Changing root retained old file identities');
 $assert(is_file($root.'/Альбом/01.mp3'),'Original file removed by reconfiguration');
 $scanner->tick();
 $failureLibrary=null;$constraintAdded=false;$trigger='music_fail_'.bin2hex(random_bytes(6));
 mkdir($root.'/Failure/A',0700,true);mkdir($root.'/Failure/Z',0700,true);
 file_put_contents($root.'/Failure/Z/z.mp3',"\xff\xfb\x90\x64".str_repeat("\0",2048));
 try{
  $repo->save(null,1,'Failure test',$root.'/Failure',true);
  $failureLibrary=$db->one('SELECT id FROM music_libraries WHERE root_path=?',[str_replace('\\','/',$root).'/Failure'])['id'];
  $pdo->exec("ALTER TABLE music_entries ADD CONSTRAINT ".$trigger." CHECK (library_id<>".$pdo->quote($failureLibrary)." OR kind<>'folder' OR relative_path<>'A')");$constraintAdded=true;
  $scanner->tick();
  $failed=$db->one('SELECT generation,scan_generation,scan_status FROM music_libraries WHERE id=?',[$failureLibrary]);
  $assert($failed['scan_status']==='failed'&&(int)$failed['generation']===0&&(int)$failed['scan_generation']===1,'Failed first batch reused generation');
  $assert((int)$db->query('SELECT COUNT(*) FROM music_entries WHERE library_id=?',[$failureLibrary])->fetchColumn()===0,'Failed scan retained partial entries');
  $pdo->exec('ALTER TABLE music_entries DROP CHECK '.$trigger);$constraintAdded=false;
  unlink($root.'/Failure/Z/z.mp3');rmdir($root.'/Failure/Z');
  $repo->rescan($failureLibrary,1);$scanner->tick();
  $recovered=$repo->browse(['library'=>$failureLibrary,'recursive'=>'1']);
  $assert($recovered['generation']===2&&$recovered['total']===0&&count($recovered['folders'])===1,'Retry published ghost files from failed batch');
 }finally{
  if($constraintAdded)$pdo->exec('ALTER TABLE music_entries DROP CHECK '.$trigger);
  if($failureLibrary)$db->query('DELETE FROM music_libraries WHERE id=?',[$failureLibrary]);
  if(is_file($root.'/Failure/Z/z.mp3'))unlink($root.'/Failure/Z/z.mp3');
  if(is_dir($root.'/Failure/Z'))rmdir($root.'/Failure/Z');
  rmdir($root.'/Failure/A');rmdir($root.'/Failure');
 }
 echo "PASS: music scan checkpoints, tree, pagination bounds, metadata, atomic publication, CAS, auth/scopes/revocation/server identity, containment, changed files, admin escaping.\n";
}finally{
 if($library)$db->query('DELETE FROM music_libraries WHERE id=?',[$library]);
 if($client){$db->query('DELETE FROM client_settings WHERE client_id=?',[$client]);$db->query('DELETE FROM clients WHERE id=?',[$client]);}
 unlink($root.'/Альбом/01.mp3');rmdir($root.'/Альбом');unlink($root.'/bad.mp3');unlink($root.'/secret.txt');unlink($root.'/tags.tmp');rmdir($root);
}
