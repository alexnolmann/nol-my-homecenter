<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
$db=new MediaCentre\Storage\Database($pdo);
$path=dirname(__DIR__,2).'/.local/h2/secrets/test.key';
if(!is_dir(dirname($path)))mkdir(dirname($path),0700,true);
file_put_contents($path,random_bytes(32));chmod($path,0600);
$store=new MediaCentre\Security\SecretStore($path,'test-v1');$id=MediaCentre\Shared\Uuid::random();$secret='synthetic-secret-'.bin2hex(random_bytes(8));
$repo=new MediaCentre\Integration\IntegrationRepository($db,$store);
$repo->create($id,'future_closed',[],['access_token'=>$secret]);
$row=$db->one('SELECT * FROM integrations WHERE id=?',[$id]);
if(str_contains($row['credentials_ciphertext'],$secret) || $store->open($id,$row)['access_token']!==$secret)throw new RuntimeException('Secret encryption failed.');
foreach([function()use($store,$row){$store->open(MediaCentre\Shared\Uuid::random(),$row);},function()use($store,$id,$row){$row['credentials_ciphertext'][0]=chr(ord($row['credentials_ciphertext'][0])^1);$store->open($id,$row);}] as $case){try{$case();throw new LogicException('Corrupt secret accepted.');}catch(RuntimeException){}}
$audit=$db->one('SELECT changes FROM audit_events WHERE object_id=?',[$id])['changes'];
if(str_contains($audit,$secret) || str_contains($audit,'access_token'))throw new RuntimeException('Audit leaked secret.');
$public=MediaCentre\Shared\Uuid::random();$repo->create($public,'cbr');
if($repo->publicView($public)['has_credentials']!==0)throw new RuntimeException('Public source has fake credentials.');
try{$repo->create(MediaCentre\Shared\Uuid::random(),'moex_iss',[],['token'=>$secret]);throw new LogicException('Public credentials accepted.');}catch(InvalidArgumentException){}
$moex=MediaCentre\Shared\Uuid::random();$instrument=MediaCentre\Shared\Uuid::random();
$repo->create($moex,'moex_iss',['data_mode'=>'unknown']);
(new MediaCentre\Finance\InstrumentRepository($db))->create($instrument,['integration_id'=>$moex,'type'=>'share','symbol'=>'SBER','exchange'=>'MOEX','board'=>'TQBR','quote_currency'=>'RUB','name'=>'Сбер']);
$quotes=new MediaCentre\Finance\QuoteRepository($db);
$q=['price'=>'123.45','nominal'=>'1','quote_as_of'=>'2026-09-27T07:30:00Z','fetched_at'=>'2026-09-27T08:00:00Z','valid_until'=>'2026-09-27T08:15:00Z','test_data'=>true];
$quotes->save($instrument,1,$q);$repo->update($moex,1,'moex_iss',['data_mode'=>'public_delayed']);
if($quotes->find($instrument)!==null || $quotes->save($instrument,1,$q)!==false)throw new RuntimeException('Old source mode quote/job survived.');
unlink($path);
echo "PASS: Sodium round trip, tamper/entity binding, secret-free audit and credential-free public providers.\n";
