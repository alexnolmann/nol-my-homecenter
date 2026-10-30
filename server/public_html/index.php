<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');
try {
    $config = require dirname(__DIR__) . '/private/bootstrap.php';
    $headers=[];
    foreach (['authorization','if-none-match','if-match','x-mediacentre-server-id'] as $name) if (isset($_SERVER['HTTP_'.strtoupper(str_replace('-','_',$name))])) $headers[$name]=$_SERVER['HTTP_'.strtoupper(str_replace('-','_',$name))];
    $path=parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH);
    $body=(string) file_get_contents('php://input',false,null,0,32769);
    $api=new MediaCentre\Http\Api($config, dirname(__DIR__));
    $response=$api->handle($_SERVER['REQUEST_METHOD'] ?? 'GET', is_string($path) ? $path : '/', $_GET, $headers, $body,$_SERVER['REMOTE_ADDR'] ?? 'unknown');
    http_response_code($response['status']);
    foreach ($response['headers'] as $name=>$value) header($name . ': ' . $value);
    if(isset($response['file'])){header_remove('Content-Type');header('Content-Type: image/jpeg');readfile($response['file']);}
    elseif ($response['body'] !== null) echo json_encode($response['body'],JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR);
} catch (Throwable) {
    http_response_code(500);
    header('Cache-Control: no-store');
    $hex=bin2hex(random_bytes(16));
    $requestId=substr($hex,0,8).'-'.substr($hex,8,4).'-4'.substr($hex,13,3).'-a'.substr($hex,17,3).'-'.substr($hex,20);
    echo json_encode(['error'=>['code'=>'INTERNAL_ERROR','message'=>'Unexpected server error.'], 'request_id'=>$requestId]);
}
