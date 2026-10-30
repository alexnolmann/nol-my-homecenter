<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';$db=new MediaCentre\Storage\Database($pdo);
$admin=$db->one('SELECT * FROM admin_users WHERE login=?',['h3-test-admin']);$pair=new MediaCentre\Security\PairingRepository($db);
$location=$db->one('SELECT id FROM locations ORDER BY created_at LIMIT 1');$template=$db->one('SELECT client_id FROM client_settings ORDER BY created_at LIMIT 1');
$db->query('INSERT IGNORE INTO app_state(id,server_id) VALUES(1,?)',[MediaCentre\Shared\Uuid::random()]);
try{$pair->issue($admin['id'],(int)$admin['auth_revision'],$location['id'],null,['home:control']);throw new RuntimeException('Home scope accepted.');}catch(InvalidArgumentException){}
$issued=$pair->issue($admin['id'],(int)$admin['auth_revision'],$location['id'],$template['client_id'],MediaCentre\Security\ClientAccess::SCOPES);
if(strlen($issued['code'])!==8 || !$db->one('SELECT id FROM pairing_codes WHERE code_hash=?',[hash('sha256',$issued['code'],true)]))throw new RuntimeException('Code policy/storage failed.');
$result=$pair->consume($issued['code'],'paired H3 test','0.3-test');
if(!$result || strlen($result['token'])!==64 || $pair->consume($issued['code'],'replay','0.3-test')!==null)throw new RuntimeException('Pairing/replay failed.');
$access=new MediaCentre\Security\ClientAccess($db);
if(!$access->authenticate('Bearer '.$result['token']) || $access->authenticate('Cookie '.$result['token']))throw new RuntimeException('Bearer boundary failed.');
$lists=new MediaCentre\Client\OrderedListRepository($db);
if($lists->readWatchlist($result['client_id'])!==$lists->readWatchlist($template['client_id']))throw new RuntimeException('Configured template not cloned.');
$pair->revoke($result['client_id'],1,$admin['id']);if($access->authenticate('Bearer '.$result['token']))throw new RuntimeException('Revoked token accepted.');
$expired=$pair->issue($admin['id'],(int)$admin['auth_revision'],$location['id'],null,[]);
$db->query('UPDATE pairing_codes SET expires_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE code_hash=?',[hash('sha256',$expired['code'],true)]);
if($pair->consume($expired['code'],'expired','0.3-test'))throw new RuntimeException('Expired code accepted.');
$audit=MediaCentre\Storage\Database::json($db->all('SELECT changes FROM audit_events WHERE admin_id=?',[$admin['id']]));
if(str_contains($audit,$result['token']) || str_contains($audit,$issued['code']))throw new RuntimeException('Pairing secret leaked.');
echo "PASS: 8-character one-use/expired pairing, scoped template clone, token hashing, revoke and secret-free audit.\n";
