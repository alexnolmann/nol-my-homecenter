param([Parameter(Mandatory)][string]$MySqlRoot,[int]$Port=3307,[string]$PhpPath='php')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runtime=Join-Path $root '.local/h2/mysql'
$data=Join-Path $runtime 'data'
if($Port -lt 1024 -or $Port -gt 65535){throw 'Expected unprivileged port.'}
$absolute=[IO.Path]::GetFullPath($data)
if(-not $absolute.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'MySQL data path escaped workspace.'}
New-Item -ItemType Directory -Path $runtime,$data -Force | Out-Null
if(Test-Path (Join-Path $runtime 'process.json')){throw 'Inspect/stop the existing own H2 MySQL process first.'}
$binary=Join-Path $MySqlRoot 'bin/mysqld.exe'
$config=Join-Path $runtime 'my.ini'
@"
[mysqld]
basedir=$($MySqlRoot.Replace('\','/'))
datadir=$($data.Replace('\','/'))
port=$Port
bind-address=127.0.0.1
mysqlx=0
default-time-zone=+00:00
innodb-buffer-pool-size=64M
log-error=$($runtime.Replace('\','/'))/error.log
pid-file=$($runtime.Replace('\','/'))/mysqld.pid
"@ | Set-Content -LiteralPath $config -Encoding ASCII
if(-not (Test-Path (Join-Path $data 'mysql'))){
    & $binary ('--defaults-file='+$config) --initialize-insecure
    if($LASTEXITCODE -ne 0){throw 'Own MySQL initialization failed; inspect private error.log.'}
}
$arguments=@('--defaults-file="'+$config+'"','--no-monitor=ON')
if(-not (Test-Path (Join-Path $runtime 'initialized.flag'))){
    & $PhpPath (Join-Path $PSScriptRoot 'write-bootstrap.php') $Port
    if($LASTEXITCODE -ne 0){throw 'Private bootstrap SQL could not be prepared.'}
    $arguments+=('--init-file="'+(Join-Path $runtime 'bootstrap.sql')+'"')
}
$child=Start-Process -FilePath $binary -ArgumentList $arguments -WorkingDirectory $runtime -WindowStyle Hidden -PassThru
@{pid=$child.Id;binary=$binary;configuration=$config;port=$Port} | ConvertTo-Json | Set-Content (Join-Path $runtime 'process.json') -Encoding UTF8
Write-Output 'Own MySQL 8.4 instance starting on loopback; private datadir only.'
