<?php
declare(strict_types=1);
$root=dirname(__DIR__,2);
$config=require $root.'/server/private/bootstrap.php';
$values=$config->values;
$variables=['host'=>'MC_TEST_DB_HOST','port'=>'MC_TEST_DB_PORT','name'=>'MC_TEST_DB_NAME','user'=>'MC_TEST_DB_USER','password'=>'MC_TEST_DB_PASSWORD'];
$testDatabase=[];
foreach($variables as $key=>$variable){$value=getenv($variable);if($value!==false)$testDatabase[$key]=$value;}
if($testDatabase!==[]){
    if(count($testDatabase)!==count($variables) || in_array('',array_values($testDatabase),true))throw new RuntimeException('All five MC_TEST_DB_* variables are required; no fallback on partial configuration.');
    if(!preg_match('/^[a-zA-Z0-9_]+_test$/D',$testDatabase['name']) || strtolower($testDatabase['user'])==='root')throw new RuntimeException('Dedicated *_test database and non-root test user required.');
    if(!ctype_digit($testDatabase['port']) || (int)$testDatabase['port']<1 || (int)$testDatabase['port']>65535)throw new RuntimeException('Invalid test MySQL port.');
    $testDatabase['port']=(int)$testDatabase['port'];
}else{
    $credentials=json_decode((string)file_get_contents($root.'/.local/h2/mysql/credentials.json'),true,512,JSON_THROW_ON_ERROR);
    $testDatabase=['host'=>'127.0.0.1','port'=>$credentials['port'],'name'=>'mediacentre_h2_test','user'=>'mc_h2','password'=>$credentials['application_password']];
}
$values['database']=$testDatabase;
$config=new MediaCentre\Shared\AppConfig($values);
$pdo=MediaCentre\Storage\Connection::open($config);
(new MediaCentre\Storage\Migrator($pdo,$root.'/server/private/migrations'))->migrate();
return [$config,$pdo];
