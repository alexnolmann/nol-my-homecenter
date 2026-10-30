param([Parameter(Mandatory)][string]$OpenServerRoot,[string]$PhpPath='php')
$ErrorActionPreference='Stop'
$project=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runtime=Join-Path $project '.local/osp-http-web'
foreach($port in @(18089,19089)){
    $probe=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port)
    try{$probe.Start()}finally{$probe.Stop()}
}
if(Test-Path -LiteralPath (Join-Path $runtime 'nginx.pid')){throw 'Inspect the prior test Nginx instance before running again.'}
& $PhpPath (Join-Path $PSScriptRoot 'check-http.php') env
if($LASTEXITCODE -ne 0){throw 'Test environment failed'}
$normalized=$runtime.Replace('\','/')
$nginx=Join-Path $OpenServerRoot 'modules/Nginx/nginx.exe'
$cgi=Join-Path $OpenServerRoot 'modules/PHP-8.4/php-cgi.exe'
$fastcgi=(Join-Path $OpenServerRoot 'modules/Nginx/conf/fastcgi.conf').Replace('\','/')
$template=[IO.File]::ReadAllText((Join-Path $project 'deploy/osp/mediacentre.miniw.conf'))
$template=$template.Replace('include fastcgi.conf;',('include "'+$fastcgi+'";')).Replace('include env/{host}.conf;',('include "'+$normalized+'/env.conf";'))
$public=(Join-Path $project 'server/public_html').Replace('\','/')
$nginxConfig=@"
pid nginx.pid;
error_log error.log warn;
events { worker_connections 32; }
http {
    access_log off;
    types { text/css css; text/javascript js; }
    client_body_temp_path "$normalized/temp";
    server {
        listen 127.0.0.1:18089;
        server_name mediacentre.miniw;
        root "$public";
        set `$backend_ip 127.0.0.1;
        set `$backend_port 19089;
        $template
    }
}
"@
New-Item -ItemType Directory -Path (Join-Path $runtime 'temp') -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $runtime 'nginx.conf'),$nginxConfig,[Text.UTF8Encoding]::new($false))
& $nginx -p ($normalized+'/') -c ($normalized+'/nginx.conf') -t
if($LASTEXITCODE -ne 0){throw 'Test Nginx config failed'}
$cgiProcess=$null;$nginxProcess=$null
try{
    $cgiProcess=Start-Process -FilePath $cgi -ArgumentList @('-b','127.0.0.1:19089','-d',('upload_tmp_dir='+$normalized+'/temp')) -WindowStyle Hidden -PassThru
    $nginxProcess=Start-Process -FilePath $nginx -ArgumentList @('-p',('"'+$normalized+'/"'),'-c',('"'+$normalized+'/nginx.conf"')) -WindowStyle Hidden -WorkingDirectory $runtime -PassThru
    Start-Sleep -Milliseconds 700
    & $PhpPath (Join-Path $PSScriptRoot 'check-http.php') verify
    if($LASTEXITCODE -ne 0){throw 'HTTP integration failed'}
}finally{
    if($nginxProcess){& $nginx -p ($normalized+'/') -c ($normalized+'/nginx.conf') -s quit}
    if($cgiProcess -and -not $cgiProcess.HasExited){Stop-Process -Id $cgiProcess.Id}
}
