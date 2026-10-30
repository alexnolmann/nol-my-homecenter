$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runtime=Join-Path $projectRoot '.local/h1/windows'
$recordsPath=Join-Path $runtime 'processes.json'
if (-not (Test-Path -LiteralPath $recordsPath)) {Write-Output 'No recorded H1 instance.'; exit}
$records=@(Get-Content -Raw $recordsPath | ConvertFrom-Json)
foreach ($record in $records) {
    $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($record.pid)"
    if (-not $process) {continue}
    if ($process.ExecutablePath -ne $record.binary -or -not $process.CommandLine.Contains($record.marker)) {throw 'PID identity changed; refusing to stop unrelated process.'}
    if ($record.binary.EndsWith('nginx.exe')) {
        $pidFile=Join-Path $runtime 'nginx.pid'
        if(Test-Path -LiteralPath $pidFile){if([int](Get-Content -Raw $pidFile) -ne $record.pid){throw 'Nginx pid file does not match verified process.'}}
        else {$record.pid | Set-Content -LiteralPath $pidFile -Encoding ASCII}
        & $record.binary -p ($runtime.Replace('\','/')+'/') -c (Join-Path $runtime 'nginx.conf') -s quit
        if ($LASTEXITCODE -ne 0) {throw 'Nginx quit failed.'}
        Wait-Process -Id $record.pid -Timeout 10 -ErrorAction SilentlyContinue
        if(Get-Process -Id $record.pid -ErrorAction SilentlyContinue){throw 'Own Nginx did not exit.'}
    } elseif ($record.binary.EndsWith('php.exe')) {
        New-Item -ItemType File -Path (Join-Path $projectRoot 'server/storage/maintenance.stop') -Force | Out-Null
        if (-not (Wait-Process -Id $record.pid -Timeout 40 -ErrorAction SilentlyContinue)) {
            if (Get-Process -Id $record.pid -ErrorAction SilentlyContinue) {throw 'Maintenance did not exit; inspect it before stopping.'}
        }
    } else {Stop-Process -Id $record.pid}
}
Remove-Item -LiteralPath $recordsPath
Write-Output 'Own H1 processes stopped; OpenServer services preserved.'
