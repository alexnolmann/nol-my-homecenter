<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(1);
$root=dirname(__DIR__,2);
// Each suite runs in its own process, including the CurlTransport function doubles.
// Repository tests deliberately mutate the dedicated test schema. Never use a working DB.
$run=static function(array $command)use($root):void{
    $process=proc_open($command,[0=>STDIN,1=>STDOUT,2=>STDERR],$pipes,$root);
    if(!is_resource($process) || proc_close($process)!==0)throw new RuntimeException('CI command failed: '.basename((string)end($command)));
};
$files=[];
foreach(['server/private','server/public_html','server/tests','server/bin'] as $directory){
    foreach(new RecursiveIteratorIterator(new RecursiveDirectoryIterator($root.'/'.$directory,FilesystemIterator::SKIP_DOTS)) as $file){
        if($file->isFile() && $file->getExtension()==='php')$files[]=$file->getPathname();
    }
}
sort($files);
foreach($files as $file)$run([PHP_BINARY,'-l',$file]);
$suites=[
    'http-transport','osp-config','h6-test-config','run','h2-repositories','h2-lists','h2-finance','h2-validation','h2-security','h2-readmodel','h2-critical','photoframe','client-title','climate','music',
    'h3-admin','h3-sessions','h3-forms','h3-pairing','h3-api','h3-boundaries','h3-concurrent',
    'h4-weather','h4-cbr','h4-moex','h4-jobs','h4-snapshots','h4-admin',
    'h5-admin','h5-catalogue','h5-finance','h5-radio',
    'h6-cache','h6-finance','h6-limits','h6-transport','h6-queue','h6-admin','weather-providers',
];
// A clean checkout must never depend on a working installation's encryption key.
$keyDirectory=$root.'/.local/testing';if(!is_dir($keyDirectory)&&!mkdir($keyDirectory,0700,true))throw new RuntimeException('Cannot create test key directory.');
$keyPath=$keyDirectory.'/ci-'.bin2hex(random_bytes(16)).'.key';$previousKey=getenv('MC_SECRET_KEY_PATH');
$keyFile=fopen($keyPath,'x');if(!$keyFile)throw new RuntimeException('Cannot create temporary test key.');
try{if(fwrite($keyFile,random_bytes(32))!==32)throw new RuntimeException('Cannot write temporary test key.');}finally{fclose($keyFile);}
chmod($keyPath,0600);putenv('MC_SECRET_KEY_PATH='.$keyPath);
try{
    foreach($suites as $suite){
        echo "RUN $suite\n";
        $run([PHP_BINARY,'-d','disable_functions=curl_init','-d','allow_url_fopen=0',__DIR__.'/'.$suite.'.php']);
    }
    $run([PHP_BINARY,$root.'/tools/check-public.php']);
    echo 'PASS: '.count($suites)." suites; fixtures, real MySQL transactions and concurrent processes.\n";
}finally{
    putenv($previousKey===false?'MC_SECRET_KEY_PATH':'MC_SECRET_KEY_PATH='.$previousKey);
    if(is_file($keyPath))unlink($keyPath);
}
