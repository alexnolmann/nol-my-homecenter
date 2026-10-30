<?php
declare(strict_types=1);
if (PHP_SAPI !== 'cli') exit(1);
foreach (['config.php','contracts.php','fixtures-api.php','admin.php'] as $file) {
    // Isolate local variables/closures while using the same locked autoloader.
    (static function(string $path): void {require $path;})(__DIR__.'/'.$file);
}
echo "PASS: server verification complete.\n";
