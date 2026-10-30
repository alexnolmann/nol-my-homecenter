<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
$db=new MediaCentre\Storage\Database($pdo);
$admin=$db->one('SELECT id,login,enabled,auth_revision FROM admin_users WHERE login=?',['h3-test-admin']);
if(!$admin)throw new RuntimeException('Run h3-admin.php first.');
$state=['admin_id'=>$admin['id'],'auth_revision'=>(int)$admin['auth_revision'],'started'=>100,'seen'=>200];
use MediaCentre\Admin\Session;
if(!Session::valid($state,$admin,300) || Session::valid($state,$admin,200+Session::IDLE) || Session::valid($state+[],array_replace($admin,['auth_revision'=>99]),300) || Session::valid($state,array_replace($admin,['enabled'=>false]),300))throw new RuntimeException('Session policy failed.');
$absolute=$state;$absolute['seen']=100+Session::ABSOLUTE;
if(Session::valid($absolute,$admin,100+Session::ABSOLUTE))throw new RuntimeException('Absolute expiry failed.');
Session::start(dirname(__DIR__).'/storage/h3-test-sessions');$before=session_id();Session::login($admin);
if(session_id()===$before || !Session::checkCsrf(Session::csrf()) || Session::checkCsrf('invalid') || Session::checkCsrf(['array']))throw new RuntimeException('ID/CSRF failed.');
$limits=new MediaCentre\Security\RateLimiter($db);$bucket='test-'.bin2hex(random_bytes(16));
if(!$limits->allow($bucket,2,60,100) || !$limits->allow($bucket,2,60,101) || $limits->allow($bucket,2,60,102) || !$limits->allow($bucket,2,60,160))throw new RuntimeException('Persistent limiter failed.');
Session::reset();if(isset($_SESSION['admin_id']))throw new RuntimeException('Logout failed.');session_write_close();
echo "PASS: strict secure cookies, ID rotation, idle/absolute/revision expiry, CSRF and MySQL limits.\n";
