<?php
declare(strict_types=1);
require dirname(__DIR__).'/vendor/autoload.php';
use MediaCentre\Admin\Access;
use MediaCentre\Admin\Session;
use MediaCentre\Shared\AppConfig;
$values=require dirname(__DIR__).'/private/config/production.php';
$values['transport']['http_host']=null;
$closed=new AppConfig($values);
$values['transport']['http_host']='mediacentre.miniw';
$http=new AppConfig($values);
$server=['HTTP_HOST'=>'mediacentre.miniw','MC_ADMIN_ALLOWED'=>'1'];
$assert=fn(bool $value,string $message)=>$value ?: throw new RuntimeException($message);
$assert(!Access::allowed($closed,$server),'HTTP enabled by default');
$assert(Access::allowed($http,$server),'Configured HTTP host rejected');
$assert(Access::allowed($http,array_replace($server,['HTTP_HOST'=>'MEDIACENTRE.MINIW:80'])),'Standard port rejected');
foreach(['evil.example','mediacentre.miniw.evil.example','mediacentre.miniw:8445','mediacentre.miniw.',''] as $host)
    $assert(!Access::allowed($http,array_replace($server,['HTTP_HOST'=>$host])),'Foreign host accepted');
$assert(!Access::allowed($http,array_replace($server,['MC_ADMIN_ALLOWED'=>'0','HTTP_X_FORWARDED_PROTO'=>'https'])),'Forwarded header granted access');
$assert(!Access::allowed($closed,$server+['HTTP_X_FORWARDED_PROTO'=>'https']),'Forwarded scheme enabled HTTP');
$assert(Access::allowed($closed,$server+['HTTPS'=>'on']),'HTTPS regression');
foreach(['http://mediacentre.miniw','mediacentre.miniw:80','mediacentre.miniw/path',true] as $host){
    $values['transport']['http_host']=$host;
    try{new AppConfig($values);throw new RuntimeException('Invalid HTTP host accepted');}catch(InvalidArgumentException){}
}
$directory=dirname(__DIR__).'/storage/mc-http-session-'.bin2hex(random_bytes(8));
try{
    foreach([false,true] as $secure){
        Session::start($directory,$secure);
        $cookie=session_get_cookie_params();
        $assert($cookie['secure']===$secure && $cookie['httponly'] && $cookie['samesite']==='Strict' && $cookie['path']==='/admin','Cookie transport policy failed');
        $before=session_id();Session::login(['id'=>'test','auth_revision'=>1]);
        $assert(session_id()!==$before && Session::checkCsrf(Session::csrf()) && !Session::checkCsrf('wrong'),'Session/CSRF regression');
        session_destroy();session_id('');$_SESSION=[];
    }
}finally{foreach(glob($directory.'/*') ?: [] as $file)unlink($file);if(is_dir($directory))rmdir($directory);}
echo "PASS: explicit HTTP host/listener, HTTPS, forwarded-header rejection, session cookies, rotation and CSRF.\n";
