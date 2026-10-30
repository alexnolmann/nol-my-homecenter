<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
$root=dirname(__DIR__,2);
$host=$argv[1] ?? 'mediacentre.miniw';
if(!filter_var($host,FILTER_VALIDATE_DOMAIN,FILTER_FLAG_HOSTNAME) || $host!==strtolower($host))throw new InvalidArgumentException('Use a lowercase DNS name without scheme, path or port.');
$bind=$argv[2] ?? '';
if(!filter_var($bind,FILTER_VALIDATE_IP,FILTER_FLAG_IPV4) || $bind==='0.0.0.0')throw new InvalidArgumentException('Pass the server LAN IPv4 address as the second argument.');
$files=[
    $root.'/.osp/project.ini'=>str_replace(['mediacentre.miniw','__BIND_IP__'],[$host,$bind],(string)file_get_contents(__DIR__.'/project.ini')),
    $root.'/.osp/env.ini'=>str_replace('mediacentre.miniw',$host,(string)file_get_contents(__DIR__.'/env.ini')),
    $root.'/.osp/nginx/'.$host.'.conf'=>(string)file_get_contents(__DIR__.'/mediacentre.miniw.conf'),
];
// Preflight every destination before writing anything. Existing machine settings are never replaced.
foreach($files as $path=>$content)if(is_file($path) && file_get_contents($path)!==$content)
    throw new RuntimeException('Existing OSP configuration differs. Merge the deploy/osp templates manually: '.basename($path));
foreach($files as $path=>$content){
    if(is_file($path))continue;
    if(!is_dir(dirname($path)) && !mkdir(dirname($path),0700,true))throw new RuntimeException('Cannot create OSP directory.');
    $stream=fopen($path,'x');if(!$stream)throw new RuntimeException('Cannot create OSP configuration.');
    try{if(fwrite($stream,$content)!==strlen($content))throw new RuntimeException('Cannot write OSP configuration.');}finally{fclose($stream);}
}
echo "OSP HTTP configuration prepared. Restart OSPanel after configuring database access.\n";
