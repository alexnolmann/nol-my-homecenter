<?php
declare(strict_types=1);
/** Checks files Git would publish, including untracked non-ignored files. Never prints matched values. */
if(PHP_SAPI!=='cli')exit(1);
$root=dirname(__DIR__);
$git=static function(array $arguments)use($root):string{
    $process=proc_open(['git','ls-files',...$arguments],[1=>['pipe','w'],2=>['pipe','w']],$pipes,$root);
    if(!is_resource($process))throw new RuntimeException('Cannot enumerate public Git repository.');
    $output=stream_get_contents($pipes[1]);stream_get_contents($pipes[2]);fclose($pipes[1]);fclose($pipes[2]);if(proc_close($process)!==0)throw new RuntimeException('Run this check inside a Git checkout.');return $output;
};
$paths=explode("\0",$git(['-z','--cached','--others','--exclude-standard']));
foreach(explode("\0",$git(['--stage','-z'])) as $entry)if(preg_match('/^(?:160000|120000) /',$entry))throw new RuntimeException('Embedded repository or symlink in index requires review.');
$count=0;
foreach(array_unique($paths) as $path){
    if($path===''||!is_file($root.'/'.$path))continue;
    if(preg_match('~(?:^|/)(?:\.local|\.osp|\.git|build|\.gradle|output)(?:/|$)|^(?:private|archive|publication)/|^\.gitmodules$|^assets/wallpapers/|^docs/private/|^server/(?:storage|vendor|private/logs)/|^server/private/config/(?:local\.php|[^/]+\.local\.php)$|(?:^|/)\.env(?:\.|$)|\.(?:log|bak|pem|key|keystore|jks|p12|pfx|apk|aab|zip|7z|dump)$~i',$path))throw new RuntimeException('Private/generated file would be published: '.$path);
    if(is_link($root.'/'.$path))throw new RuntimeException('Symlink requires explicit review: '.$path);
    $body=(string)file_get_contents($root.'/'.$path);
    if(preg_match('~-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----|\bgh[pousr]_[A-Za-z0-9]{30,}|\bgithub_pat_[A-Za-z0-9_]{40,}|\bAKIA[0-9A-Z]{16}\b~',$body))throw new RuntimeException('Possible credential; contents suppressed: '.$path);
    $count++;
}
echo "PASS: $count publishable files; private/generated paths and credential patterns excluded.\n";
