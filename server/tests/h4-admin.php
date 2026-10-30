<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
use MediaCentre\Storage\Database;
use MediaCentre\Shared\Uuid;
use MediaCentre\Jobs\{CollectionQueue,CollectionStatus};
$db=new Database($pdo);$db->query('DELETE FROM collection_targets');$db->query("DELETE FROM jobs WHERE queue='collection'");
$location=Uuid::random();$client=Uuid::random();$locations=new MediaCentre\Weather\LocationRepository($db);$locations->create($location,'H4 status <script>','59.938630','30.314130','Europe/Moscow');(new MediaCentre\Client\ClientRepository($db))->create($client,'status client',$location);
$queue=new CollectionQueue($db);$key='weather:'.$location;$queue->sync(array_values(array_filter(MediaCentre\Jobs\CollectionPlanner::describe($db),fn($target)=>$target['key']===$key)));
$forms=new MediaCentre\Admin\FormActions($db);$stable=serialize($db->all('SELECT * FROM collection_targets'));
$view=$forms->view();if(serialize($db->all('SELECT * FROM collection_targets'))!==$stable || !$view['collections'][0]['current_configuration'])throw new RuntimeException('Reading administration planned or mutated a target.');
$forms->save(['action'=>'refresh','target_key'=>$key]);$forms->save(['action'=>'refresh','target_key'=>$key]);
if((int)$db->query("SELECT COUNT(*) FROM jobs WHERE queue='collection'")->fetchColumn()!==1)throw new RuntimeException('Refresh did not immediately queue/dedupe one job.');
$job=$queue->claim(Uuid::random());$queue->finish($job,[$key=>['status'=>'failed','error'=>'RATE_LIMITED']],120);$before=$db->one('SELECT * FROM collection_targets WHERE target_key=?',[$key]);
$forms->save(['action'=>'refresh','target_key'=>$key]);$after=$db->one('SELECT * FROM collection_targets WHERE target_key=?',[$key]);
if($after['next_due_at']!==$before['next_due_at'] || $after['job_id']!==null || (int)$db->query("SELECT COUNT(*) FROM jobs WHERE queue='collection'")->fetchColumn()!==1)throw new RuntimeException('Refresh bypassed provider Retry-After.');
$admin=['login'=>'status-test'];$section='overview';$message='';$csrf='test-csrf';$view=$forms->view();$e=MediaCentre\Admin\Pages::escape(...);ob_start();require dirname(__DIR__).'/private/views/admin/panel.php';$html=ob_get_clean();
if(!str_contains($html,'Состояние источников') || !str_contains($html,'RATE_LIMITED') || str_contains($html,'H4 status <script>') || !str_contains($html,'&lt;script&gt;'))throw new RuntimeException('Source status/escaping missing.');
$key='weather:'.$location; // The included view has its own navigation loop variable named key.
$locations->update($location,1,'Changed','59.9','30.3','Europe/Moscow');
if(CollectionStatus::read($db)[0]['current_configuration'])throw new RuntimeException('Old configuration still reported successful/current.');
try{$forms->save(['action'=>'refresh','target_key'=>$key]);throw new LogicException('Old target manually refreshed.');}catch(OutOfBoundsException){}
foreach(['https://example.invalid/','weather:bad'] as $bad){try{$forms->save(['action'=>'refresh','target_key'=>$bad]);throw new LogicException('Refresh accepted provider URL.');}catch(InvalidArgumentException){}}
$queue->sync(array_values(array_filter(MediaCentre\Jobs\CollectionPlanner::describe($db),fn($target)=>$target['key']===$key)));
$admin=$db->one('SELECT id,auth_revision FROM admin_users WHERE enabled=1 LIMIT 1');if(!$admin)throw new RuntimeException('Run H3 suites first.');$db->adminActor=$admin['id'];$db->adminAuthRevision=(int)$admin['auth_revision']-1;
try{$forms->save(['action'=>'refresh','target_key'=>$key]);throw new LogicException('Revoked admin queued a job.');}catch(MediaCentre\Security\AccessDenied){}finally{$db->adminActor=null;$db->adminAuthRevision=null;}
if(function_exists('curl_init') || filter_var(ini_get('allow_url_fopen'),FILTER_VALIDATE_BOOL))throw new RuntimeException('Provider networking must be disabled for form test.');
echo "PASS H4 administration: read-only escaped source states, immediate queued refresh/dedupe, Retry-After, invalidated configuration and revoked admin; provider transport disabled.\n";
