<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
$path=dirname(__DIR__,2).'/.local/h2/secrets/master.key';
if(is_file($path)){if(filesize($path)!==32)throw new RuntimeException('Existing key invalid; never overwritten.');echo "Existing external key retained.\n";exit;}
if(!is_dir(dirname($path)))mkdir(dirname($path),0700,true);
$file=fopen($path,'x');
if(!$file)throw new RuntimeException('Cannot create external key.');
$key=random_bytes(32);fwrite($file,$key);fclose($file);chmod($path,0600);sodium_memzero($key);
echo "External Sodium key created outside database/Git; keep it with the private backup.\n";
