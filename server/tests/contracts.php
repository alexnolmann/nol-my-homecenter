<?php
declare(strict_types=1);
require dirname(__DIR__) . '/vendor/autoload.php';
$validator = new MediaCentre\Shared\ContractValidator(dirname(__DIR__, 2) . '/contracts/v1/schema.json');
foreach (['0','12345678901234567890.123456789','-1.50'] as $value) if (!$validator->valid('decimal', $value)) throw new RuntimeException('Exact decimal rejected.');
foreach ([12.4, '1e3','NaN','01.2','+2'] as $value) if ($validator->valid('decimal', $value)) throw new RuntimeException('Inexact/noncanonical decimal accepted.');
if (!$validator->valid('current', (object)['temperature_c'=>null,'condition'=>'unknown','future_field'=>42])) throw new RuntimeException('Nullable measurement / additive field rejected.');
if ($validator->valid('features', (object)['weather'=>true,'radio'=>true,'finance'=>true,'home'=>true])) throw new RuntimeException('Home enabled.');
if (!$validator->valid('utc','2026-09-27T09:30:00Z') || $validator->valid('utc','2026-09-27T09:30:00+03:00')) throw new RuntimeException('UTC boundary violated.');
if ($validator->valid('preferencesPatch',(object)['revision'=>1,'finance_symbols'=>['SBER']])) throw new RuntimeException('Unexpected settings write accepted.');
echo "PASS: decimal precision, NULL, additive fields, UTC, disabled home and write boundaries.\n";
