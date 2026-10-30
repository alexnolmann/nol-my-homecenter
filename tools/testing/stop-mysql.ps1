param([Parameter(Mandatory)][string]$PhpPath,[switch]$BootstrapOnly)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runtime=Join-Path $root '.local/h2/mysql'
$recordPath=Join-Path $runtime 'process.json'
$record=Get-Content -Raw $recordPath | ConvertFrom-Json
$processes=@(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object { $_.ExecutablePath -eq $record.binary -and $_.CommandLine.Contains($record.configuration) })
foreach($process in $processes){
    if($BootstrapOnly){
        if((Test-Path (Join-Path $runtime 'data/mediacentre_h2_test')) -or (Test-Path (Join-Path $runtime 'data/mediacentre_h2_demo'))){throw 'Bootstrap fallback is forbidden after application schemas exist.'}
        Stop-Process -Id $process.ProcessId
        Wait-Process -Id $process.ProcessId -Timeout 30 -ErrorAction SilentlyContinue
    }
}
$process=Get-CimInstance Win32_Process -Filter "ProcessId=$($record.pid)"
if($process){
    if($process.ExecutablePath -ne $record.binary -or -not $process.CommandLine.Contains($record.configuration)){throw 'Own MySQL process identity changed.'}
    if($BootstrapOnly){
        if((Test-Path (Join-Path $runtime 'data/mediacentre_h2_test')) -or (Test-Path (Join-Path $runtime 'data/mediacentre_h2_demo'))){throw 'Bootstrap fallback is forbidden after application schemas exist.'}
        Stop-Process -Id $record.pid
    }else{
        & $PhpPath (Join-Path $PSScriptRoot 'shutdown-mysql.php')
        if($LASTEXITCODE -ne 0){throw 'Graceful shutdown failed; inspect own instance.'}
    }
    Wait-Process -Id $record.pid -Timeout 30 -ErrorAction SilentlyContinue
    if(Get-Process -Id $record.pid -ErrorAction SilentlyContinue){throw 'Own MySQL did not exit.'}
}
Remove-Item -LiteralPath $recordPath
Write-Output 'Own H2 MySQL stopped.'
