<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
$runtime=dirname(__DIR__,2).'/.local/h2/mysql';
$credentials=json_decode((string)file_get_contents($runtime.'/credentials.json'),true,512,JSON_THROW_ON_ERROR);
try{$pdo=new PDO('mysql:host=127.0.0.1;port='.$credentials['port'],'mc_h2_shutdown',$credentials['shutdown_password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_TIMEOUT=>3]);$pdo->exec('SHUTDOWN');echo "Own MySQL graceful shutdown requested.\n";}catch(Throwable){echo "Own MySQL shutdown unavailable; credentials/diagnostics suppressed.\n";exit(1);}
