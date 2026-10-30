<?php
declare(strict_types=1);
namespace MediaCentre\Jobs;

final class Maintenance
{
    public static function tick(string $storage,?\MediaCentre\Storage\Database $db=null,?\MediaCentre\Providers\Transport $http=null): void
    {
        $jobs=0;
        self::publish($storage,0,$db!==null);
        if($db!==null){$queue=new CollectionQueue($db);$queue->sync(CollectionPlanner::describe($db));$queue->enqueue();$jobs=(new CollectionWorker($db,$http ?? new \MediaCentre\Providers\CurlTransport()))->run(3,fn($count)=>self::publish($storage,$count,true),fn()=>is_file($storage.'/maintenance.stop'));$queue->cleanup();(new \MediaCentre\Music\MusicScanner($db))->tick();}
        self::publish($storage,$jobs,$db!==null);
    }
    private static function publish(string $storage,int $jobs,bool $collection): void
    {
        if (!is_dir($storage) && !mkdir($storage, 0700, true) && !is_dir($storage)) throw new \RuntimeException('Storage unavailable.');
        $temporary = tempnam($storage, '.heartbeat-');
        if ($temporary === false) throw new \RuntimeException('Cannot create heartbeat.');
        try {
            if (file_put_contents($temporary, json_encode(['updated_at'=>gmdate('Y-m-d\TH:i:s\Z'), 'stage'=>$collection?'H4':'H1', 'collection_jobs'=>$jobs], JSON_THROW_ON_ERROR)) === false || !rename($temporary, $storage . '/maintenance.json')) throw new \RuntimeException('Cannot persist heartbeat.');
        } finally { if (is_file($temporary)) unlink($temporary); }
    }
}
