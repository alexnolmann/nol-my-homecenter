<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
$root=dirname(__DIR__,2);
$temporary=$root.'/server/storage/osp-config-'.bin2hex(random_bytes(8));
mkdir($temporary.'/deploy/osp',0700,true);
$templates=['prepare.php','project.ini','env.ini','mediacentre.miniw.conf'];
$assert=fn(bool $ok,string $message)=>$ok ?: throw new RuntimeException($message);
$run=function(array $args)use($temporary):int{
    $process=proc_open([PHP_BINARY,$temporary.'/deploy/osp/prepare.php',...$args],[0=>['pipe','r'],1=>['pipe','w'],2=>['pipe','w']],$pipes);
    if(!is_resource($process))throw new RuntimeException('Cannot run OSP preparation');
    fclose($pipes[0]);stream_get_contents($pipes[1]);stream_get_contents($pipes[2]);fclose($pipes[1]);fclose($pipes[2]);return proc_close($process);
};
try{
    foreach($templates as $file)copy($root.'/deploy/osp/'.$file,$temporary.'/deploy/osp/'.$file);
    $assert($run(['http://invalid','192.168.1.50'])!==0 && !is_dir($temporary.'/.osp'),'Invalid domain changed configuration');
    $assert($run(['panel.miniw','0.0.0.0'])!==0 && !is_dir($temporary.'/.osp'),'Unspecified bind address accepted');
    $assert($run(['panel.miniw','192.168.1.50'])===0,'Preparation failed');
    $project=(string)file_get_contents($temporary.'/.osp/project.ini');
    $assert(str_contains($project,'[panel.miniw]') && str_contains($project,'bind_ip = 192.168.1.50') && str_contains($project,'{base_dir}') && !str_contains($project,'__BIND_IP__'),'Project template substitution failed');
    $assert(str_contains((string)file_get_contents($temporary.'/.osp/env.ini'),'MC_HTTP_HOST = panel.miniw'),'HTTP host opt-in missing');
    $assert(file_get_contents($temporary.'/.osp/nginx/panel.miniw.conf')===file_get_contents($root.'/deploy/osp/mediacentre.miniw.conf'),'Nginx template changed');
    $assert($run(['panel.miniw','192.168.1.50'])===0,'Idempotent preparation failed');
    file_put_contents($temporary.'/.osp/env.ini','existing settings');
    $assert($run(['panel.miniw','192.168.1.50'])!==0,'Existing machine settings overwritten');
    $assert(file_get_contents($temporary.'/.osp/env.ini')==='existing settings' && file_get_contents($temporary.'/.osp/project.ini')===$project,'Failed preflight partially changed settings');
}finally{
    foreach(['.osp/project.ini','.osp/env.ini','.osp/nginx/panel.miniw.conf',...array_map(fn($file)=>'deploy/osp/'.$file,$templates)] as $file)if(is_file($temporary.'/'.$file))unlink($temporary.'/'.$file);
    foreach(['.osp/nginx','.osp','deploy/osp','deploy',''] as $directory)if(is_dir($temporary.'/'.$directory))rmdir($temporary.'/'.$directory);
}
echo "PASS: OSP domain/IP templates, HTTP opt-in, repeatability and preservation of machine settings.\n";
