<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
use MediaCentre\Storage\Database;
use MediaCentre\Jobs\{CollectionQueue,CollectionWorker,LeaseLost};
use MediaCentre\Shared\Uuid;
use MediaCentre\Providers\{Transport,HttpResult,ProviderError};
$db=new Database($pdo);$db->query('DELETE FROM collection_targets');$db->query("DELETE FROM jobs WHERE queue='collection'");
$location=Uuid::random();$other=Uuid::random();
(new MediaCentre\Weather\LocationRepository($db))->create($location,'Job A','59.938630','30.314130','Europe/Moscow');
(new MediaCentre\Weather\LocationRepository($db))->create($other,'Job B','59.938630','30.314130','Europe/Moscow');
$a=(new MediaCentre\Weather\LocationRepository($db))->find($location);$b=(new MediaCentre\Weather\LocationRepository($db))->find($other);
$descriptions=[['key'=>'weather:'.$location,'kind'=>'weather','id'=>$location,'context'=>$a],['key'=>'weather:'.$other,'kind'=>'weather','id'=>$other,'context'=>$b]];
$queue=new CollectionQueue($db);$queue->sync($descriptions);if($queue->enqueue()!==2 || $queue->enqueue()!==0)throw new RuntimeException('Collection dedupe failed.');
$owner=Uuid::random();$job=$queue->claim($owner);$second=$queue->claim(Uuid::random());if(!$job || !$second || $job['id']===$second['id'] || $queue->claim(Uuid::random())!==null)throw new RuntimeException('Exclusive job claim failed.');
$target=json_decode($job['payload'],true,64,JSON_THROW_ON_ERROR)['targets'][0];
$db->query('UPDATE jobs SET lease_until=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE id=?',[$job['id']]);
$reclaimed=$queue->claim(Uuid::random());if(!$reclaimed || $reclaimed['id']!==$job['id'])throw new RuntimeException('Expired lease not reclaimed.');
$db->jobActor=['id'=>$job['id'],'owner'=>$owner];
try{(new MediaCentre\Weather\SnapshotRepository($db))->failure($target['context']['id'],1,'TIMEOUT',gmdate('Y-m-d\TH:i:s\Z'));throw new LogicException('Expired worker wrote a snapshot.');}catch(LeaseLost){}
try{$queue->finish($job,[$target['key']=>['status'=>'success']]);throw new LogicException('Expired worker finished reclaimed job.');}catch(LeaseLost){}finally{$db->jobActor=null;}
$queue->finish($reclaimed,[$target['key']=>['status'=>'failed','error'=>'RATE_LIMITED']],120);
$secondTarget=json_decode($second['payload'],true,64,JSON_THROW_ON_ERROR)['targets'][0];$queue->finish($second,[$secondTarget['key']=>['status'=>'success','has_data'=>true]]);
$failed=$db->one('SELECT * FROM collection_targets WHERE target_key=?',[$target['key']]);$success=$db->one('SELECT * FROM collection_targets WHERE target_key=?',[$secondTarget['key']]);
if($failed['failure_count']!==1 || $failed['last_error_code']!=='RATE_LIMITED' || $failed['last_success_at']!==null || $success['failure_count']!==0 || !$success['last_success_at'])throw new RuntimeException('Independent outcome/backoff failed.');
if(CollectionQueue::delay(1)!==30 || CollectionQueue::delay(20)!==3600 || CollectionQueue::delay(2,120)!==120)throw new RuntimeException('Bounded exponential/Retry-After delay failed.');
$db->query('UPDATE collection_targets SET next_due_at=UTC_TIMESTAMP(3)');if($queue->enqueue()!==2)throw new RuntimeException('Completed dedupe key was not released.');
$captured=$queue->claim(Uuid::random());$old=json_decode($captured['payload'],true,64,JSON_THROW_ON_ERROR)['targets'][0];
$new=$descriptions;foreach($new as &$description)if($description['key']===$old['key'])$description['context']['name']='Updated config';unset($description);
$queue->sync($new);$queue->finish($captured,[$old['key']=>['status'=>'success']]);
$changed=$db->one('SELECT * FROM collection_targets WHERE target_key=?',[$old['key']]);if($changed['job_id']!==null || $changed['next_due_at']>gmdate('Y-m-d H:i:s',time()+1))throw new RuntimeException('Changed configuration did not reschedule.');
// Real worker error path must call HTTP outside SQL and back off independently.
$db->query('UPDATE jobs SET lease_until=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE status=?',['leased']);
$http=new class($db) implements Transport{public int $requests=0;public function __construct(private Database $db){}public function get(string $url):HttpResult{if($this->db->pdo->inTransaction())throw new LogicException('Worker network in SQL transaction.');$this->requests++;throw new ProviderError('TIMEOUT');}};
if((new CollectionWorker($db,$http))->run(1)!==1 || $http->requests!==1)throw new RuntimeException('Worker did not execute isolated request.');
// Eleven instruments on one source/board must become two ISS jobs, never one oversized request.
$db->query('DELETE FROM collection_targets');$db->query("DELETE FROM jobs WHERE queue='collection'");$source=Uuid::random();$descriptions=[];
for($n=0;$n<11;$n++){$id=Uuid::random();$descriptions[]=['key'=>'moex:'.$id,'kind'=>'moex','id'=>$id,'context'=>['id'=>$id,'revision'=>1,'integration_id'=>$source,'board'=>'TQBR','symbol'=>'TEST'.$n]];}
$queue->sync($descriptions);if($queue->enqueue()!==2)throw new RuntimeException('ISS batch size did not split at ten.');
foreach($db->all("SELECT payload FROM jobs WHERE queue='collection'") as $row)if(count(json_decode($row['payload'],true)['targets'])>10)throw new RuntimeException('Oversized ISS job persisted.');
$db->query('DELETE FROM collection_targets');$db->query("DELETE FROM jobs WHERE queue='collection'");
// Two real PHP workers must skip a held row and cannot claim the same remaining job.
$queue->sync([['key'=>'weather:'.$location,'kind'=>'weather','id'=>$location,'context'=>$a],['key'=>'weather:'.$other,'kind'=>'weather','id'=>$other,'context'=>$b]]);$queue->enqueue();
$pdo->beginTransaction();$held=$db->one("SELECT id FROM jobs WHERE queue='collection' ORDER BY id LIMIT 1 FOR UPDATE")['id'];
$directory=dirname(__DIR__,2).'/.local/h4/concurrency';if(!is_dir($directory))mkdir($directory,0700,true);$id=Uuid::random();$workers=[];
try{
    foreach(['a','b'] as $slot){$process=proc_open([PHP_BINARY,__DIR__.'/h4-job-worker.php',$id,$slot],[1=>['pipe','w'],2=>['pipe','w']],$pipes);if(!is_resource($process))throw new RuntimeException('Job worker launch failed.');$workers[$slot]=[$process,$pipes];}
    $deadline=microtime(true)+10;while((!is_file($directory.'/'.$id.'.a.result') || !is_file($directory.'/'.$id.'.b.result')) && microtime(true)<$deadline)usleep(20000);
    if(!is_file($directory.'/'.$id.'.a.result') || !is_file($directory.'/'.$id.'.b.result'))throw new RuntimeException('Concurrent claim waited on skipped lock.');
    $results=[];foreach($workers as $slot=>[$process,$pipes]){$output=stream_get_contents($pipes[1]).stream_get_contents($pipes[2]);fclose($pipes[1]);fclose($pipes[2]);proc_close($process);if($output!=='')throw new RuntimeException('Concurrent job worker failed.');$results[]=file_get_contents($directory.'/'.$id.'.'.$slot.'.result');}
    sort($results);if(!str_starts_with($results[0],'claimed:') || $results[0]==='claimed:'.$held || $results[1]!=='empty')throw new RuntimeException('Concurrent claim exclusivity failed.');
}finally{if($pdo->inTransaction())$pdo->rollBack();}
$db->query('DELETE FROM collection_targets');$db->query("DELETE FROM jobs WHERE queue='collection'");
echo "PASS H4 jobs: dedupe, exclusive claims, expired lease recovery and fencing, independent Retry-After/backoff, changed configuration, outside-SQL worker and ten-symbol batches.\n";
