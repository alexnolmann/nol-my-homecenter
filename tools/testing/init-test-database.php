<?php
declare(strict_types=1);
if (PHP_SAPI!=='cli') exit(1);
$root=dirname(__DIR__,2);$runtime=$root.'/.local/h2/mysql';
$process=json_decode((string)file_get_contents($runtime.'/process.json'),true,512,JSON_THROW_ON_ERROR);
$path=$runtime.'/credentials.json';
$credentials=is_file($path)?json_decode((string)file_get_contents($path),true,512,JSON_THROW_ON_ERROR):['root_password'=>bin2hex(random_bytes(32)),'application_password'=>bin2hex(random_bytes(32)),'port'=>$process['port']];
file_put_contents($path,json_encode($credentials,JSON_THROW_ON_ERROR),LOCK_EX);chmod($path,0600);
$pdo=null;
for($attempt=0;$attempt<30;$attempt++) {
    try {$pdo=new PDO('mysql:host=127.0.0.1;port='.$process['port'].';dbname=mediacentre_h2_test','mc_h2',$credentials['application_password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_TIMEOUT=>1]);break;}catch(Throwable){}
    usleep(200000);
}
if(!$pdo){echo "Own MySQL not ready; credentials/driver messages suppressed.\n";exit(1);}
$pdo->query('SELECT 1 FROM information_schema.schemata WHERE schema_name=\'mediacentre_h2_demo\'')->fetchColumn() ?: throw new RuntimeException('Own demo schema missing.');
file_put_contents($runtime.'/initialized.flag','ready');
if(is_file($runtime.'/bootstrap.sql'))unlink($runtime.'/bootstrap.sql');
echo "Own test/demo schemas ready; random passwords stored outside Git; existing MySQL unchanged.\n";
