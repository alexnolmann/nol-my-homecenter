<?php
declare(strict_types=1);
require dirname(__DIR__) . '/vendor/autoload.php';
use MediaCentre\Shared\AppConfig;

foreach (['development', 'production'] as $environment) {
    $values = require dirname(__DIR__) . '/private/config/' . $environment . '.php';
    $config = new AppConfig($values);
    if ($config->values['features']['home'] !== false) throw new RuntimeException('Home must be disabled.');
    if ($environment === 'production' && $config->values['fixtures']) throw new RuntimeException('Fixtures leaked to production.');
}
foreach ([['environment'=>'production','fixtures'=>true,'features'=>['home'=>false]], ['environment'=>'development','features'=>['home'=>true]]] as $invalid) {
    try { new AppConfig($invalid); throw new RuntimeException('Unsafe configuration accepted.'); }
    catch (InvalidArgumentException) {}
}
echo "PASS: development/production isolation; fixtures and home guards.\n";
