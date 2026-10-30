param([switch]$Install, [switch]$Verify)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$jdkDirectories = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot '.local/jdk') -Directory)
if ($jdkDirectories.Count -ne 1) {
    throw 'Expected one JDK 17 directory under .local/jdk.'
}
$env:JAVA_HOME = $jdkDirectories[0].FullName
$env:ANDROID_HOME = Join-Path $projectRoot '.local/android-sdk'
$env:ANDROID_USER_HOME = Join-Path $projectRoot '.local/android-user-home'
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.local/gradle-cache'

$signingDirectory = Join-Path $projectRoot '.local/signing'
New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
$debugKeystore = Join-Path $signingDirectory 'h0-debug.keystore'
if (-not (Test-Path -LiteralPath $debugKeystore)) {
    & (Join-Path $env:JAVA_HOME 'bin/keytool.exe') -genkeypair -keystore $debugKeystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Android Debug,O=Local H0,C=XX'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create local H0 debug key.' }
}

Push-Location (Join-Path $projectRoot 'android')
try {
    if ($Verify) {
        & './gradlew.bat' --no-daemon :app:clean :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :probe:assembleDebug
    } else {
        & './gradlew.bat' --no-daemon :app:assembleDebug
    }
    if ($LASTEXITCODE -ne 0) { throw 'APK build failed.' }
} finally {
    Pop-Location
}

$apkPath = Join-Path $projectRoot 'android/app/build/outputs/apk/debug/app-debug.apk'
$signerPath = Join-Path $env:ANDROID_HOME 'build-tools/35.0.0/apksigner.bat'
& $signerPath verify --verbose $apkPath
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }

if ($Install) {
    $adbPath = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
    & $adbPath install -r $apkPath
    if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
    $launch = & $adbPath shell am start -W -n 'local.mediacentre.client.dev/local.mediacentre.client.ui.MainActivity' 2>&1
    $launch | Write-Output
    if ($LASTEXITCODE -ne 0 -or ($launch -match 'Error:|Error type')) { throw 'Activity start failed.' }
}

Get-Item -LiteralPath $apkPath | Select-Object FullName,Length
