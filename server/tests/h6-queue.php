<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
use MediaCentre\Storage\Database;
use MediaCentre\Jobs\{CollectionQueue,LeaseLost};
use MediaCentre\Shared\Uuid;
$db=new Database($pdo);$queue=new CollectionQueue($db);$keys=[];$jobs=[];
$assert=static fn(bool $ok,string $why)=>$ok ?: throw new RuntimeException($why);
$enabled=$db->all('SELECT target_key,enabled FROM collection_targets');
$otherJobs=$db->all("SELECT id,run_after,lease_until FROM jobs WHERE queue='collection' AND status IN ('queued','leased')");
try{
    foreach($otherJobs as $job)$db->query('UPDATE jobs SET run_after=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 DAY),lease_until=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 DAY) WHERE id=?',[$job['id']]);
    $source=Uuid::random();$descriptions=[];
    for($n=0;$n<40;$n++){$id=Uuid::random();$key='moex:'.$id;$keys[]=$key;$descriptions[]=['key'=>$key,'kind'=>'moex','id'=>$id,'context'=>['id'=>$id,'revision'=>1,'integration_id'=>$source,'board'=>'TQBR','symbol'=>'TEST'.$n]];}
    $weatherId=Uuid::random();$weather='weather:'.$weatherId;$keys[]=$weather;
    $descriptions[]=['key'=>$weather,'kind'=>'weather','id'=>$weatherId,'context'=>['id'=>$weatherId,'revision'=>1]];
    $queue->sync($descriptions);
    foreach(array_slice($keys,0,40) as $key)$db->query('UPDATE collection_targets SET next_due_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY) WHERE target_key=?',[$key]);
    $assert($queue->enqueue(1)===1,'Single scheduling slot unused');
    $target=$db->one('SELECT * FROM collection_targets WHERE target_key=?',[$weather]);
    $assert($target['job_id']!==null,'Due weather starved behind older ISS targets during scheduling');
    for($n=0;$n<10;$n++)$queue->request($weather);
    $assert($db->one('SELECT job_id FROM collection_targets WHERE target_key=?',[$weather])['job_id']===$target['job_id'],'Repeated refresh created another job');
    $queue->enqueue(30);
    foreach(array_slice($keys,0,40) as $key){$id=$db->query('SELECT job_id FROM collection_targets WHERE target_key=?',[$key])->fetchColumn();if($id)$db->query('UPDATE jobs SET run_after=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY) WHERE id=?',[$id]);}
    $claimed=$queue->claim(Uuid::random());
    $assert($claimed!==null && $claimed['id']===$target['job_id'],'Due weather starved behind queued ISS jobs during claim');
    $db->query('UPDATE jobs SET lease_until=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 SECOND) WHERE id=?',[$claimed['id']]);
    $replacement=$queue->claim(Uuid::random());$assert($replacement['id']===$claimed['id'] && $replacement['owner']!==$claimed['owner'],'Expired weather lease not reclaimed');
    try{$queue->finish($claimed,[$weather=>['status'=>'success']]);throw new LogicException('Expired worker could finish');}catch(LeaseLost){}
    $queue->finish($replacement,[$weather=>['status'=>'failed','error'=>'RATE_LIMITED']],300);
    $before=$db->one('SELECT * FROM collection_targets WHERE target_key=?',[$weather]);
    for($n=0;$n<10;$n++)$queue->request($weather);
    $after=$db->one('SELECT * FROM collection_targets WHERE target_key=?',[$weather]);
    $assert($after['job_id']===null && $after['next_due_at']===$before['next_due_at'] && strtotime($after['next_due_at'].' UTC')>=time()+290,'Manual refresh bypassed provider 429');
    $next=$queue->claim(Uuid::random());$assert($next!==null && $next['type']==='moex','Weather backoff prevented finance progress');
    echo "PASS H6 queue: weather scheduling/claim ahead of ISS backlog, deduplicated manual refresh, expired lease fencing, 429 backoff cannot be bypassed, finance proceeds while weather waits.\n";
}finally{
    foreach($keys as $key){$row=$db->one('SELECT job_id FROM collection_targets WHERE target_key=?',[$key]);if($row && $row['job_id'])$jobs[$row['job_id']]=true;}
    if(isset($target['job_id']))$jobs[$target['job_id']]=true;
    foreach($keys as $key)$db->query('DELETE FROM collection_targets WHERE target_key=?',[$key]);
    foreach(array_keys($jobs) as $id)$db->query('DELETE FROM jobs WHERE id=?',[$id]);
    foreach($enabled as $row)$db->query('UPDATE collection_targets SET enabled=? WHERE target_key=?',[$row['enabled'],$row['target_key']]);
    foreach($otherJobs as $job)$db->query('UPDATE jobs SET run_after=?,lease_until=? WHERE id=?',[$job['run_after'],$job['lease_until'],$job['id']]);
}
