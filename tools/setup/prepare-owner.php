<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
$root=dirname(__DIR__,2);$config=require $root.'/server/private/bootstrap.php';
if(in_array($config->values['database']['name'],['mediacentre_h2_test','mediacentre_h2_demo'],true))throw new RuntimeException('Working database required.');
try{
    $db=new MediaCentre\Storage\Database(MediaCentre\Storage\Connection::open($config));
    if((int)$db->query('SELECT COUNT(*) FROM admin_users')->fetchColumn()>0){echo "Existing working administrator preserved.\n";exit;}
    $password=bin2hex(random_bytes(18));$login='owner';$path=$root.'/.local/h3/owner-access.json';
    if(!is_dir(dirname($path)) && !mkdir(dirname($path),0700,true))throw new RuntimeException('Private credential directory unavailable.');
    if(is_file($path))throw new RuntimeException('Owner credential artifact exists; inspect first.');
    // Persist the randomly generated initial credential before account creation; never print it.
    file_put_contents($path,json_encode(['login'=>$login,'password'=>$password],JSON_THROW_ON_ERROR),LOCK_EX);chmod($path,0600);
    $id=(new MediaCentre\Security\AdminRepository($db))->bootstrap($login,$password);
    file_put_contents($path,json_encode(['id'=>$id,'login'=>$login,'password'=>$password],JSON_THROW_ON_ERROR),LOCK_EX);
    echo "Working owner created with a unique random initial password in ignored owner-access.json.\n";
}catch(Throwable){fwrite(STDERR,"Owner preparation stopped; inspect account/artifact state. No secret printed.\n");exit(1);}
