param([Parameter(Mandatory)][string]$OpenServerRoot, [int]$Port=8081, [int]$FastCgiPort=9071,[int]$AdminPort=8082)
$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runtime=Join-Path $projectRoot '.local/h1/windows'
New-Item -ItemType Directory -Path $runtime,(Join-Path $runtime 'temp'),(Join-Path $runtime 'logs') -Force | Out-Null
if (Test-Path (Join-Path $runtime 'processes.json')) {throw 'Inspect/stop the existing H1 instance before starting another.'}
if ($Port -lt 1024 -or $Port -gt 65535 -or $FastCgiPort -lt 1024 -or $FastCgiPort -gt 65535 -or $AdminPort -lt 1024 -or $AdminPort -gt 65535 -or @($Port,$FastCgiPort,$AdminPort | Sort-Object -Unique).Count -ne 3) {throw 'Invalid local ports.'}
$php=Join-Path $OpenServerRoot 'modules/PHP-8.4/php.exe'
$cgi=Join-Path $OpenServerRoot 'modules/PHP-8.4/php-cgi.exe'
$nginx=Join-Path $OpenServerRoot 'modules/Nginx/nginx.exe'
foreach ($binary in @($php,$cgi,$nginx)) {if (-not (Test-Path -LiteralPath $binary)) {throw 'Required OpenServer binary not found.'}}
if (-not (Test-Path (Join-Path $projectRoot 'server/private/config/local.php'))) {throw 'Run tools/setup/init-local.php first.'}
$public=(Join-Path $projectRoot 'server/public_html').Replace('\','/')
$prefix=$runtime.Replace('\','/') + '/'
$config=Join-Path $runtime 'nginx.conf'
$template=Get-Content -Raw (Join-Path $PSScriptRoot 'nginx.conf')
$template=$template.Replace('__PORT__', "$Port").Replace('__FCGI_PORT__', "$FastCgiPort").Replace('__ADMIN_PORT__',"$AdminPort").Replace('__PUBLIC__', $public)
[IO.File]::WriteAllText($config,$template,[Text.UTF8Encoding]::new($false))
& $nginx -p $prefix -c $config -t
if ($LASTEXITCODE -ne 0) {throw 'Nginx config check failed.'}
$records=@()
try {
    $child=Start-Process -FilePath $cgi -ArgumentList @('-b',"127.0.0.1:$FastCgiPort",'-d','cgi.fix_pathinfo=0','-d','display_errors=0','-d','memory_limit=768M','-d','max_execution_time=300','-d','max_input_time=300','-d','post_max_size=256M','-d','upload_max_filesize=24M','-d',('upload_tmp_dir='+((Join-Path $runtime 'temp').Replace('\','/')))) -Environment @{PHP_FCGI_MAX_REQUESTS='0'} -WindowStyle Hidden -PassThru
    $records += @{pid=$child.Id; binary=$cgi; marker="127.0.0.1:$FastCgiPort"}
    $console=Join-Path $projectRoot 'server/bin/console.php'
    $stopFile=Join-Path $projectRoot 'server/storage/maintenance.stop'
    if (Test-Path -LiteralPath $stopFile) {Remove-Item -LiteralPath $stopFile}
    $child=Start-Process -FilePath $php -ArgumentList @(('"'+$console+'"'),'maintenance') -WindowStyle Hidden -PassThru
    $records += @{pid=$child.Id; binary=$php; marker=$console}
    $child=Start-Process -FilePath $nginx -ArgumentList @('-p',('"'+$prefix+'"'),'-c',('"'+$config+'"')) -WorkingDirectory $runtime -WindowStyle Hidden -PassThru -RedirectStandardError (Join-Path $runtime 'startup-error.log')
    $records += @{pid=$child.Id; binary=$nginx; marker=$config}
    $records | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime 'processes.json') -Encoding UTF8
    Start-Sleep -Milliseconds 500
    if ($child.HasExited) {throw 'Nginx exited; inspect the private startup-error.log.'}
    Write-Output "H1 origin: http://127.0.0.1:$Port/ (loopback only, PHP-CGI on Windows)."
} catch {
    $records | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime 'processes.json') -Encoding UTF8
    throw
}
