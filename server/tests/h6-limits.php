<?php
declare(strict_types=1);
require dirname(__DIR__).'/private/bootstrap.php';
use MediaCentre\Providers\ResponseBuffer;
use MediaCentre\Jobs\CollectionQueue;
$assert=static fn(bool $ok,string $message)=>$ok ?: throw new RuntimeException($message);
$buffer=new ResponseBuffer();$chunk=str_repeat('я',1024);
for($n=0;$n<1024;$n++)$assert($buffer->append($chunk)===2048,'In-limit UTF-8 bytes rejected');
$assert(strlen($buffer->body)===ResponseBuffer::MAX_BYTES,'Limit counted characters instead of bytes');
$assert($buffer->append('x')===0 && $buffer->overflow && strlen($buffer->body)===ResponseBuffer::MAX_BYTES,'Overflow was buffered');
$now=strtotime('2026-10-03T12:00:00Z');
foreach(['120'=>120,'9999999999999999999999999'=>86400,'0'=>1,'Sat, 03 Oct 2026 12:05:00 GMT'=>300,'Sat, 03 Oct 2026 11:59:00 GMT'=>1,'invalid'=>null,'-20'=>null,'Sun, 03 Oct 2026 12:05:00 GMT'=>null] as $value=>$expected){
    $buffer->header("HTTP/1.1 429 Too Many Requests\r\n");$line="Retry-After: $value\r\n";
    $assert($buffer->header($line,$now)===strlen($line) && $buffer->retryAfter===$expected,'Retry-After normalization failed');
    if($expected!==null)$assert(CollectionQueue::delay(2,$buffer->retryAfter)>=max(60,$expected),'Queue shortened Retry-After');
}
$buffer->header("Retry-After: 120\r\n");$buffer->header("HTTP/1.1 200 OK\r\n");$assert($buffer->retryAfter===null,'Previous response headers leaked');
$assert(CollectionQueue::delay(999,PHP_INT_MAX)===86400 && CollectionQueue::delay(999)===3600,'Backoff unbounded');
echo "PASS H6 limits: decoded UTF-8 body capped at 2 MiB before append; numeric/date Retry-After, malformed/past dates, response isolation and bounded queue delay.\n";
