<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
$db=new MediaCentre\Storage\Database($pdo);$repo=new MediaCentre\Security\AdminRepository($db);
$db->query('DELETE FROM auth_limits'); // Only isolated test-schema counters; start a repeatable test run.
// This isolated schema has no production administrator. Repeat runs remove only this test account/audit.
$login='h3-test-admin';$prior=$db->one('SELECT id FROM admin_users WHERE login=?',[$login]);
if($prior){
    $db->query('DELETE t FROM pairing_profiles t JOIN pairing_codes p ON p.id=t.pairing_id WHERE p.issued_by=?',[$prior['id']]);
    $db->query('DELETE FROM pairing_codes WHERE issued_by=?',[$prior['id']]);
    $db->query('DELETE FROM audit_events WHERE admin_id=?',[$prior['id']]);$db->query('DELETE FROM admin_users WHERE id=?',[$prior['id']]);
}
$password=bin2hex(random_bytes(18));$id=$repo->bootstrap($login,$password);
if(!$repo->authenticate($login,$password) || $repo->authenticate($login,'wrong') || $repo->authenticate('unknown',$password))throw new RuntimeException('Authentication boundary failed.');
try{$repo->bootstrap('another-admin',$password);throw new RuntimeException('Duplicate bootstrap accepted.');}catch(LogicException){}
foreach(['short',str_repeat('a',73),"long-enough\0but-invalid"] as $bad){try{MediaCentre\Security\AdminRepository::password($bad);throw new RuntimeException('Bad password accepted.');}catch(InvalidArgumentException){}}
$db->query('UPDATE admin_users SET password_hash=? WHERE id=?',[password_hash($password,PASSWORD_BCRYPT,['cost'=>4]),$id]);
$repo->authenticate($login,$password);
if(password_needs_rehash($db->one('SELECT password_hash FROM admin_users WHERE id=?',[$id])['password_hash'],PASSWORD_DEFAULT))throw new RuntimeException('Rehash failed.');
$new=bin2hex(random_bytes(18));$repo->changePassword($id,1,$password,$new);
if($repo->authenticate($login,$password) || !$repo->authenticate($login,$new) || $repo->find($id)['auth_revision']!==2)throw new RuntimeException('Password revision failed.');
$audit=MediaCentre\Storage\Database::json($db->all('SELECT changes FROM audit_events WHERE admin_id=?',[$id]));
if(str_contains($audit,$password) || str_contains($audit,$new) || str_contains($audit,'password_hash'))throw new RuntimeException('Secret leaked into audit.');
echo "PASS: first-admin guard, password verify/rehash, policy, revision and secret-free audit.\n";
