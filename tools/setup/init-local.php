<?php
declare(strict_types=1);
if (PHP_SAPI !== 'cli') exit(1);
$root = dirname(__DIR__, 2);
$path = $root . '/server/private/config/local.php';
if (is_file($path)) { echo "Local config already exists; preserved.\n"; exit(0); }
$database = ['host'=>'127.0.0.1','port'=>3306,'name'=>null,'user'=>null,'password'=>null];
if (isset($argv[1])) {
    $aliases = ['host'=>'host','dbhost'=>'host','port'=>'port','user'=>'user','dbuser'=>'user','pass'=>'password','dbpass'=>'password','password'=>'password','db'=>'name','database'=>'name','name'=>'name','dbname'=>'name','db_name'=>'name','db name'=>'name','database_name'=>'name','database name'=>'name'];
    $pending = null;
    foreach (file($argv[1], FILE_IGNORE_NEW_LINES) ?: [] as $line) {
        $line = trim($line);
        if ($line === '' || str_starts_with($line, '```')) continue;
        $label = strtolower(trim($line, "#: \t"));
        if (isset($aliases[$label])) {$pending=$aliases[$label]; continue;}
        if ($pending !== null) {$database[$pending]=trim($line, '`'); $pending=null;}
    }
    $addresses = gethostbynamel($database['host']);
    if (!$addresses || array_filter($addresses, static fn(string $ip): bool => !str_starts_with($ip, '127.'))) throw new RuntimeException('Only local DB credentials can be imported.');
    $database['port'] = (int) $database['port'];
}
$local = ['environment'=>'development','development'=>['token'=>bin2hex(random_bytes(32)), 'trusted_proxy'=>false], 'database'=>$database];
if (file_put_contents($path, "<?php\nreturn " . var_export($local, true) . ";\n", LOCK_EX) === false) throw new RuntimeException('Cannot write ignored local configuration.');
chmod($path, 0600);
echo "Ignored development configuration created; token/DB credentials suppressed.\n";
