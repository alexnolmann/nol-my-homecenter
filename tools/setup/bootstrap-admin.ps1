param([Parameter(Mandatory)][string]$Login,[string]$PhpPath='php')
$ErrorActionPreference='Stop'
if($Login -notmatch '^[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}$'){throw 'Expected 3–64 ASCII login characters.'}
$secret=Read-Host 'Пароль администратора (12–72 байта)' -AsSecureString
$confirm=Read-Host 'Повторите пароль' -AsSecureString
$first=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
$second=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($confirm)
try {
    $password=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($first)
    if($password -cne [Runtime.InteropServices.Marshal]::PtrToStringBSTR($second)){throw 'Passwords differ.'}
    $script=(Join-Path $PSScriptRoot 'bootstrap-admin.php')
    $start=[Diagnostics.ProcessStartInfo]::new($PhpPath)
    $start.ArgumentList.Add($script);$start.ArgumentList.Add($Login)
    $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardInput=$true
    $start.StandardInputEncoding=[Text.UTF8Encoding]::new($false)
    $process=[Diagnostics.Process]::Start($start)
    $process.StandardInput.WriteLine($password);$process.StandardInput.Close();$process.WaitForExit()
    if($process.ExitCode -ne 0){throw 'Bootstrap failed.'}
} finally {
    $password=$null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($first)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($second)
    $secret.Dispose();$confirm.Dispose()
}
