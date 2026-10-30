<?php
declare(strict_types=1);
// Invalid test configuration must fail before any database connection or migration.
$environment=getenv();
foreach(array_keys($environment) as $key)if(str_starts_with($key,'MC_TEST_DB_'))unset($environment[$key]);
$base=['MC_TEST_DB_HOST'=>'127.0.0.1','MC_TEST_DB_PORT'=>'1','MC_TEST_DB_NAME'=>'isolated_test','MC_TEST_DB_USER'=>'test_user','MC_TEST_DB_PASSWORD'=>'not-a-real-password'];
$cases=[
    [['MC_TEST_DB_HOST'=>'127.0.0.1'],'All five MC_TEST_DB_*'],
    [array_replace($base,['MC_TEST_DB_NAME'=>'working_database']),'Dedicated *_test'],
    [array_replace($base,['MC_TEST_DB_USER'=>'root']),'non-root test user'],
    [array_replace($base,['MC_TEST_DB_PORT'=>'0']),'Invalid test MySQL port'],
];
foreach($cases as [$variables,$expected]){
    $process=proc_open([PHP_BINARY,'-r','require '.var_export(__DIR__.'/h2-bootstrap.php',true).';'],[1=>['pipe','w'],2=>['pipe','w']],$pipes,null,array_replace($environment,$variables));
    if(!is_resource($process))throw new RuntimeException('Test-config worker unavailable.');
    $output=stream_get_contents($pipes[1]).stream_get_contents($pipes[2]);fclose($pipes[1]);fclose($pipes[2]);
    if(proc_close($process)===0 || !str_contains($output,$expected) || str_contains($output,'SQLSTATE'))throw new RuntimeException('Unsafe test configuration reached database access or was not rejected.');
}
echo "PASS: partial test configuration, working schema, root and invalid port rejected before database access.\n";
