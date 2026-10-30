<?php
declare(strict_types=1);
[$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
$id=MediaCentre\Validation\Input::uuid($argv[1] ?? null);$slot=$argv[2] ?? '';
if(!in_array($slot,['a','b'],true))exit(1);
$path=dirname(__DIR__,2).'/.local/h4/concurrency/'.$id.'.'.$slot;
try{$job=(new MediaCentre\Jobs\CollectionQueue(new MediaCentre\Storage\Database($pdo)))->claim(MediaCentre\Shared\Uuid::random());file_put_contents($path.'.result',$job===null?'empty':'claimed:'.$job['id'],LOCK_EX);}
catch(Throwable){file_put_contents($path.'.result','failed',LOCK_EX);exit(1);}
