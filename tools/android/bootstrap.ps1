param([switch]$InstallSdk, [switch]$AcceptSdkLicense)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$downloads = Join-Path $projectRoot '.local/downloads'
$sdkRoot = Join-Path $projectRoot '.local/android-sdk'
New-Item -ItemType Directory -Path $downloads,$sdkRoot -Force | Out-Null

function Get-VerifiedArchive {
    param([string]$Url, [string]$Path, [string]$Checksum, [string]$Algorithm)
    if (-not (Test-Path -LiteralPath $Path)) {
        Invoke-WebRequest -Uri $Url -OutFile $Path -UseBasicParsing
    }
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm $Algorithm).Hash.ToLowerInvariant()
    if ($actual -ne $Checksum.ToLowerInvariant()) {
        throw "Checksum mismatch: $Path. Remove this local download and retry."
    }
}

$jdkRoot = Join-Path $projectRoot '.local/jdk'
if (-not (Test-Path -LiteralPath $jdkRoot)) {
    $jdkAsset = (Invoke-RestMethod -Uri 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse')[0]
    $jdkAsset | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $downloads 'jdk17-metadata.json') -Encoding UTF8
    $jdkArchive = Join-Path $downloads 'jdk17.zip'
    Get-VerifiedArchive -Url $jdkAsset.binary.package.link -Path $jdkArchive -Checksum $jdkAsset.binary.package.checksum -Algorithm SHA256
    Expand-Archive -LiteralPath $jdkArchive -DestinationPath $jdkRoot
}
$jdkDirectories = @(Get-ChildItem -LiteralPath $jdkRoot -Directory)
if ($jdkDirectories.Count -ne 1) { throw 'Expected one local JDK 17 directory.' }
$env:JAVA_HOME = $jdkDirectories[0].FullName
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_USER_HOME = Join-Path $projectRoot '.local/android-user-home'

$commandLineRoot = Join-Path $sdkRoot 'cmdline-tools/12.0'
$sdkManager = Join-Path $commandLineRoot 'cmdline-tools/bin/sdkmanager.bat'
if (-not (Test-Path -LiteralPath $sdkManager)) {
    # Checksum published in Google's Android repository metadata for Windows 12.0.
    $toolsArchive = Join-Path $downloads 'commandline-tools.zip'
    Get-VerifiedArchive -Url 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -Path $toolsArchive -Checksum '3d2917302740f476999a091bc5558837c7a863c5' -Algorithm SHA1
    Expand-Archive -LiteralPath $toolsArchive -DestinationPath $commandLineRoot
}

if ($InstallSdk) {
    if ($AcceptSdkLicense) {
        1..12 | ForEach-Object { 'y' } | & $sdkManager --sdk_root=$sdkRoot 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0'
    } else {
        & $sdkManager --sdk_root=$sdkRoot 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0'
    }
    if ($LASTEXITCODE -ne 0) { throw 'SDK installation failed.' }
}

& (Join-Path $env:JAVA_HOME 'bin/java.exe') -version
Write-Output 'Toolchains remain local to .local/. Use deploy/h0/build-android.ps1 to build.'
