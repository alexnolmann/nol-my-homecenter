<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
$runtime=dirname(__DIR__,2).'/.local/h2/mysql';$path=$runtime.'/credentials.json';
$values=is_file($path)?json_decode((string)file_get_contents($path),true,512,JSON_THROW_ON_ERROR):['root_password'=>bin2hex(random_bytes(32)),'application_password'=>bin2hex(random_bytes(32)),'port'=>(int)($argv[1] ?? 3307)];
if(!preg_match('/^[a-f0-9]{64}$/D',$values['root_password']))throw new RuntimeException('Unexpected local root password format.');
file_put_contents($path,json_encode($values,JSON_THROW_ON_ERROR),LOCK_EX);chmod($path,0600);
$values['shutdown_password'] ??= bin2hex(random_bytes(32));
foreach(['root_password','application_password','shutdown_password'] as $key)if(!preg_match('/^[a-f0-9]{64}$/D',$values[$key]))throw new RuntimeException('Unexpected private credential format.');
file_put_contents($path,json_encode($values,JSON_THROW_ON_ERROR),LOCK_EX);
$sql="ALTER USER 'root'@'localhost' IDENTIFIED BY '".$values['root_password']."';\n";
$sql.="CREATE USER IF NOT EXISTS 'mc_h2'@'127.0.0.1' IDENTIFIED BY '".$values['application_password']."';\n";
foreach(['mediacentre_h2_test','mediacentre_h2_demo'] as $schema){
    $sql.="CREATE DATABASE IF NOT EXISTS $schema CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n";
    $sql.="GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON $schema.* TO 'mc_h2'@'127.0.0.1';\n";
}
$sql.="CREATE USER IF NOT EXISTS 'mc_h2_shutdown'@'127.0.0.1' IDENTIFIED BY '".$values['shutdown_password']."';\nGRANT SHUTDOWN ON *.* TO 'mc_h2_shutdown'@'127.0.0.1';\n";
file_put_contents($runtime.'/bootstrap.sql',$sql,LOCK_EX);chmod($runtime.'/bootstrap.sql',0600);
echo "Private first-start SQL prepared; credentials suppressed.\n";
