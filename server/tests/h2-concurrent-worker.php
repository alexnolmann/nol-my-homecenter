<?php
declare(strict_types=1);
try{
    [$config,$pdo]=require __DIR__.'/h2-bootstrap.php';
    $client=MediaCentre\Validation\Input::uuid($argv[1] ?? null);
    $path=dirname(__DIR__,2).'/.local/h2/concurrency/'.basename($argv[2] ?? '');
    file_put_contents($path.'.ready','ready',LOCK_EX);
    try{(new MediaCentre\Client\SettingsRepository(new MediaCentre\Storage\Database($pdo)))->update($client,1,['radio_volume'=>66]);$result='incorrect-success';}
    catch(MediaCentre\Storage\RevisionConflict){$result='revision-conflict';}
    file_put_contents($path.'.result',$result,LOCK_EX);
}catch(Throwable){echo "Concurrent worker failed; diagnostics suppressed.\n";exit(1);}
