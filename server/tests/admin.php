<?php
declare(strict_types=1);
require dirname(__DIR__).'/vendor/autoload.php';
use MediaCentre\Admin\Access;
use MediaCentre\Admin\Pages;
use MediaCentre\Shared\AppConfig;
$development=new AppConfig(require dirname(__DIR__).'/private/config/development.php');
$production=new AppConfig(require dirname(__DIR__).'/private/config/production.php');
foreach([$development,$production] as $config)if(!Access::allowed($config,['HTTPS'=>'on','MC_ADMIN_ALLOWED'=>'1']) || Access::allowed($config,['REMOTE_ADDR'=>'127.0.0.1']))throw new RuntimeException('HTTPS/admin listener boundary failed.');
foreach ([['REMOTE_ADDR'=>'192.0.2.1'],['REMOTE_ADDR'=>'127.0.0.1','HTTP_X_FORWARDED_FOR'=>'127.0.0.1'],['REMOTE_ADDR'=>'127.0.0.1','HTTP_FORWARDED'=>'for=127.0.0.1'],['REMOTE_ADDR'=>'127.0.0.1','MC_ADMIN_ALLOWED'=>'0']] as $server) if (Access::allowed($development,$server)) throw new RuntimeException('Admin proxy/LAN boundary failed.');
$fixtures=(new MediaCentre\Shared\FixtureRepository(dirname(__DIR__,2).'/contracts/v1/fixtures'))->load('success');
$fixtures['weather']['data']['location']['name']='<img src=x onerror=alert(1)>';
ob_start();Pages::render('weather','success',$fixtures,[]);$html=ob_get_clean();
if (str_contains($html,'<img src=x') || !str_contains($html,'&lt;img') || str_contains($html,'<form')) throw new RuntimeException('Unsafe template rendering.');
echo "PASS: admin HTTPS listener boundary; historical fixture template escaping.\n";
