<?php
declare(strict_types=1);
if (PHP_SAPI !== 'cli') exit(1);
$config = require dirname(__DIR__) . '/private/bootstrap.php';
$storage = dirname(__DIR__) . '/storage';
$command = $argv[1] ?? 'help';
if ($command === 'check') {
    $result = MediaCentre\Shared\Readiness::check($config, $storage);
    echo json_encode($result, JSON_THROW_ON_ERROR) . "\n";
    exit($result['ready'] ? 0 : 1);
}
if ($command === 'maintenance') {
    $running = true;
    if (function_exists('pcntl_async_signals')) {
        pcntl_async_signals(true);
        pcntl_signal(SIGTERM, static function () use (&$running): void {$running = false;});
        pcntl_signal(SIGINT, static function () use (&$running): void {$running = false;});
    }
    do {
        try{$db=new MediaCentre\Storage\Database(MediaCentre\Storage\Connection::open($config));MediaCentre\Jobs\Maintenance::tick($storage,$db);}
        catch(Throwable){fwrite(STDERR,"Maintenance pass unavailable; credentials and provider details suppressed.\n");}
        if (in_array('--once', $argv, true)) break;
        for ($i=0; $i<10 && $running; $i++) {
            if (is_file($storage . '/maintenance.stop')) {$running=false; break;}
            sleep(1);
        }
    } while ($running);
    echo "Maintenance scheduler/worker stopped.\n";
    exit(0);
}
echo "Usage: php bin/console.php maintenance [--once] | check\n";
exit($command === 'help' ? 0 : 1);
