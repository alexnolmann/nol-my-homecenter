<?php
declare(strict_types=1);
if (PHP_SAPI !== 'cli') exit(1);
$directory = dirname(__DIR__, 2) . '/.local/h1';
if (!is_dir($directory)) mkdir($directory, 0700, true);
$path = $directory . '/compose.env';
if (is_file($path)) {echo "Private Compose environment already exists; preserved.\n"; exit(0);}
$values = ['MC_DEV_TOKEN'=>bin2hex(random_bytes(32)), 'MC_DB_PASSWORD'=>bin2hex(random_bytes(32)), 'MC_DB_ROOT_PASSWORD'=>bin2hex(random_bytes(32))];
$output = '';
foreach ($values as $key=>$value) $output .= "$key=$value\n";
file_put_contents($path, $output, LOCK_EX);
chmod($path, 0600);
echo "Private Compose environment generated; secrets suppressed.\n";
