<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';$db=new MediaCentre\Storage\Database($pdo);
$admin=$db->one('SELECT * FROM admin_users WHERE login=?',['h3-test-admin']);$location=$db->one('SELECT id FROM locations ORDER BY created_at LIMIT 1');$pair=new MediaCentre\Security\PairingRepository($db);
$directory=dirname(__DIR__,2).'/.local/h3/concurrency';if(!is_dir($directory))mkdir($directory,0700,true);
foreach(['pair','expired','limit'] as $mode){
    $id=MediaCentre\Shared\Uuid::random();$path=$directory.'/'.$id;
    $pairMode=$mode!=='limit';
    $input=$pairMode?['mode'=>'pair','code'=>$pair->issue($admin['id'],(int)$admin['auth_revision'],$location['id'],null,[])['code']]:['mode'=>$mode,'bucket'=>'concurrent-'.$id];
    file_put_contents($path.'.json',json_encode($input,JSON_THROW_ON_ERROR),LOCK_EX);$workers=[];
    if($pairMode){$pdo->beginTransaction();$db->query('SELECT id FROM pairing_codes WHERE code_hash=? FOR UPDATE',[hash('sha256',$input['code'],true)])->fetch();}
    if($mode==='expired')$db->query('UPDATE pairing_codes SET expires_at=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 2 SECOND) WHERE code_hash=?',[hash('sha256',$input['code'],true)]);
    try{
        foreach(['a','b'] as $slot){$process=proc_open([PHP_BINARY,__DIR__.'/h3-concurrent-worker.php',$id,$slot],[1=>['pipe','w'],2=>['pipe','w']],$pipes);if(!is_resource($process))throw new RuntimeException('Worker launch failed.');$workers[$slot]=[$process,$pipes];}
        $deadline=microtime(true)+15;
        while((!is_file($path.'.a.ready') || !is_file($path.'.b.ready')) && microtime(true)<$deadline)usleep(20000);
        if(!is_file($path.'.a.ready') || !is_file($path.'.b.ready'))throw new RuntimeException('Workers not ready.');
        if($pairMode){usleep($mode==='expired'?2200000:100000);if(is_file($path.'.a.result') || is_file($path.'.b.result'))throw new RuntimeException('Pair lock bypassed.');$pdo->commit();}
        $results=[];
        foreach($workers as $slot=>[$process,$pipes]){
            $out=stream_get_contents($pipes[1]);$err=stream_get_contents($pipes[2]);fclose($pipes[1]);fclose($pipes[2]);proc_close($process);
            if($out!=='' || $err!=='' || !is_file($path.'.'.$slot.'.result'))throw new RuntimeException('Concurrent worker failed.');$results[]=file_get_contents($path.'.'.$slot.'.result');
        }
        sort($results);$expected=match($mode){'pair'=>['paired','rejected'],'expired'=>['rejected','rejected'],default=>['allowed','limited']};
        if($results!==$expected)throw new RuntimeException('Concurrent auth operation not atomic.');
    }finally{if($pdo->inTransaction())$pdo->rollBack();if(is_file($path.'.json'))unlink($path.'.json');}
}
$issued=$pair->issue($admin['id'],(int)$admin['auth_revision'],$location['id'],null,[]);$before=(int)$db->query('SELECT COUNT(*) FROM clients')->fetchColumn();
$cutoff=$pdo->quote((string)$db->query('SELECT MAX(occurred_at) FROM audit_events')->fetchColumn());
$db->query("ALTER TABLE audit_events ADD CONSTRAINT h3_injected_audit_failure CHECK(action <> 'pair_use' OR occurred_at <= $cutoff)");
usleep(20000);
try{
    try{$pair->consume($issued['code'],'must rollback','h3');throw new LogicException('Injected audit failure accepted.');}catch(PDOException){}
    $row=$db->one('SELECT used_at FROM pairing_codes WHERE code_hash=?',[hash('sha256',$issued['code'],true)]);
    if($row['used_at']!==null || (int)$db->query('SELECT COUNT(*) FROM clients')->fetchColumn()!==$before)throw new RuntimeException('Partially committed pairing survived.');
}finally{$db->query('ALTER TABLE audit_events DROP CHECK h3_injected_audit_failure');}
if(!$pair->consume($issued['code'],'retry after rollback','h3'))throw new RuntimeException('Failed transaction burned pairing code.');
echo "PASS: two-process one-use pairing, expiry during lock wait and rate limit; SQL failure after code use/audit rolls back client, settings and code together.\n";
