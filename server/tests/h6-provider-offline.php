<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
use MediaCentre\Storage\Database;
use MediaCentre\Shared\Uuid;
use MediaCentre\Providers\{Transport,HttpResult,CurlTransport};
use MediaCentre\Jobs\{CollectionQueue,CollectionWorker};
use MediaCentre\Weather\{LocationRepository,WeatherCollector};
$db=new Database($pdo);$id=Uuid::random();$client=Uuid::random();$key='weather:'.$id;
$locations=new LocationRepository($db);$locations->create($id,'H6 offline fixture','52.500000','13.400000','Europe/Berlin');$location=$locations->find($id);
(new MediaCentre\Client\ClientRepository($db))->create($client,'H6 outbound failure',$id);
$token=bin2hex(random_bytes(32));$db->query('UPDATE clients SET token_hash=?,scopes=? WHERE id=?',[hash('sha256',$token,true),Database::json(MediaCentre\Security\ClientAccess::SCOPES),$client]);
$raw=(string)file_get_contents(dirname(__DIR__,2).'/contracts/providers/h4/open-meteo.json');
$fixture=new class($raw) implements Transport {public function __construct(private string $raw){}public function get(string $url):HttpResult{return new HttpResult($this->raw,'2026-03-27T23:18:00Z');}};
$queue=new CollectionQueue($db);$savedEnv=[];$listener=null;$jobId=null;$enabledBefore=$db->all('SELECT target_key,enabled FROM collection_targets');
try{
    (new WeatherCollector($db,$fixture))->collect($location);
    $fields='payload,data_as_of,fetched_at,valid_until';$before=$db->one("SELECT $fields FROM weather_snapshots WHERE location_id=?",[$id]);
    // Isolated test DB only. Other existing targets are not run by this worker.
    $queue->sync([['key'=>$key,'kind'=>'weather','id'=>$id,'context'=>$location]]);$queue->enqueue(1,$key);
    $jobId=$db->one('SELECT job_id FROM collection_targets WHERE target_key=?',[$key])['job_id'];
    $otherJobs=$db->all("SELECT id,status,run_after,lease_until FROM jobs WHERE queue='collection' AND id<>? AND status IN ('queued','leased')",[$jobId]);
    foreach($otherJobs as $job)$db->query("UPDATE jobs SET run_after=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 DAY),lease_until=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 DAY) WHERE id=?",[$job['id']]);
    // A private local listener accepts no proxy exchange: real libcurl must time out.
    // Environment is limited to this PHP process; no firewall/global proxy changes.
    $listener=stream_socket_server('tcp://127.0.0.1:0',$errno,$error);
    if(!$listener)throw new RuntimeException('Local failure endpoint unavailable.');
    $proxy='http://'.stream_socket_get_name($listener,false);
    foreach(['https_proxy'=>$proxy,'HTTPS_PROXY'=>$proxy,'all_proxy'=>$proxy,'ALL_PROXY'=>$proxy,'no_proxy'=>'','NO_PROXY'=>''] as $name=>$value){$savedEnv[$name]=getenv($name);putenv($name.'='.$value);}
    $http=new CurlTransport();$start=microtime(true);$count=(new CollectionWorker($db,$http))->run(1);
    $target=$db->one('SELECT * FROM collection_targets WHERE target_key=?',[$key]);
    if($count!==1||$http->requests!==1||$target['last_error_code']!=='TIMEOUT'||(int)$target['failure_count']!==1||$target['job_id']!==null||strtotime($target['next_due_at'].' UTC')<time()+20||microtime(true)-$start>20)throw new RuntimeException('Real outbound timeout/backoff not observed: '.json_encode(['jobs'=>$count,'requests'=>$http->requests,'error'=>$target['last_error_code'],'failures'=>$target['failure_count'],'released'=>$target['job_id']===null,'due_seconds'=>strtotime($target['next_due_at'].' UTC')-time()],JSON_THROW_ON_ERROR));
    if($db->one("SELECT $fields FROM weather_snapshots WHERE location_id=?",[$id])!==$before)throw new RuntimeException('Outbound failure changed cached data/source chronology.');
    $api=new MediaCentre\Http\Api($config,dirname(__DIR__));$response=$api->handle('GET','/api/v1/weather',[],['authorization'=>'Bearer '.$token],'');
    if($response['status']!==200||$response['body']['meta']['fetched_at']!=='2026-03-27T23:18:00Z'||$response['body']['meta']['freshness']!=='stale')throw new RuntimeException('Read API depends on outbound Internet or lost stale snapshot.');
    (new WeatherCollector($db,$fixture))->collect($location);
    if($db->one('SELECT last_error_code FROM weather_snapshots WHERE location_id=?',[$id])['last_error_code']!==null)throw new RuntimeException('Successful recovery retained source error.');
    echo "PASS H6 server outbound loss: real libcurl timeout via local non-forwarding proxy; job backoff; safe stale API, unchanged source dates; deterministic collector recovery.\n";
}finally{
    foreach($savedEnv as $name=>$value)putenv($value===false?$name:$name.'='.$value);
    if(is_resource($listener))fclose($listener);
    foreach($otherJobs??[] as $job)$db->query('UPDATE jobs SET run_after=?,lease_until=? WHERE id=?',[$job['run_after'],$job['lease_until'],$job['id']]);
    foreach($enabledBefore as $target)$db->query('UPDATE collection_targets SET enabled=? WHERE target_key=?',[$target['enabled'],$target['target_key']]);
    $db->query('DELETE FROM collection_targets WHERE target_key=?',[$key]);if($jobId!==null)$db->query('DELETE FROM jobs WHERE id=?',[$jobId]);
    $db->query('DELETE FROM client_settings WHERE client_id=?',[$client]);$db->query('DELETE FROM clients WHERE id=?',[$client]);$db->query('DELETE FROM weather_snapshots WHERE location_id=?',[$id]);$db->query('DELETE FROM locations WHERE id=?',[$id]);
}
