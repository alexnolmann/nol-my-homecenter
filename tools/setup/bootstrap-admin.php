<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
// Password comes from the hidden prompt wrapper, never argv, URL or environment.
if(count($argv)!==2 || stream_isatty(STDIN)){fwrite(STDERR,"Use tools/setup/bootstrap-admin.ps1 -Login <login> (hidden password prompt).\n");exit(1);}
try{
    $config=require dirname(__DIR__,2).'/server/private/bootstrap.php';
    $password=rtrim((string)fgets(STDIN,256),"\r\n");
    $db=new MediaCentre\Storage\Database(MediaCentre\Storage\Connection::open($config));
    (new MediaCentre\Security\AdminRepository($db))->bootstrap($argv[1],$password);
    if(function_exists('sodium_memzero'))sodium_memzero($password);
    echo "First administrator created. No password was logged.\n";
}catch(Throwable){fwrite(STDERR,"Bootstrap refused: check login/password policy, migration and existing administrator.\n");exit(1);}
