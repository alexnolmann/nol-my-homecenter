<?php
declare(strict_types=1);
try{
    [$config,$pdo]=require __DIR__.'/h2-bootstrap.php';$db=new MediaCentre\Storage\Database($pdo);
    $id=MediaCentre\Validation\Input::uuid($argv[1] ?? null);$path=dirname(__DIR__,2).'/.local/h3/concurrency/'.$id;
    $input=json_decode((string)file_get_contents($path.'.json'),true,512,JSON_THROW_ON_ERROR);$slot=$argv[2] ?? '';
    if(!in_array($slot,['a','b'],true))throw new InvalidArgumentException('Invalid worker slot.');
    file_put_contents($path.'.'.$slot.'.ready','ready',LOCK_EX);
    if($input['mode']==='pair')$result=(new MediaCentre\Security\PairingRepository($db))->consume($input['code'],'concurrent pair','h3')?'paired':'rejected';
    else $result=(new MediaCentre\Security\RateLimiter($db))->allow($input['bucket'],1,600)?'allowed':'limited';
    file_put_contents($path.'.'.$slot.'.result',$result,LOCK_EX);
}catch(Throwable){fwrite(STDERR,"Concurrent auth worker failed; details suppressed.\n");exit(1);}
