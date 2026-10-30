$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Add-Type -AssemblyName System.IO.Compression.FileSystem
$debug=Join-Path $root 'android/app/build/outputs/apk/debug/app-debug.apk'
$releaseDirectory=Join-Path $root 'android/app/build/outputs/apk/release'
$release=@(Get-ChildItem -LiteralPath $releaseDirectory -Filter '*.apk' -File)
if($release.Count -ne 1){throw 'Expected one release APK.'}
foreach($item in @(@{path=$debug;debug=$true},@{path=$release[0].FullName;debug=$false})){
    $zip=[IO.Compression.ZipFile]::OpenRead($item.path)
    try{
        $hasCa=[bool]$zip.GetEntry('assets/development-ca.pem')
        if(-not $item.debug -and $hasCa){throw 'Development CA leaked into release.'}
        if($item.debug -and (Test-Path (Join-Path $root 'android/app/src/debug/assets/development-ca.pem')) -and -not $hasCa){throw 'Debug CA not packaged.'}
        if(@($zip.Entries | Where-Object {$_.FullName.StartsWith('lib/')}).Count -ne 0){throw 'Unexpected native ABI dependency.'}
        if(-not $item.debug){
            $entry=$zip.GetEntry('classes.dex');$stream=$entry.Open();$memory=[IO.MemoryStream]::new()
            try{$stream.CopyTo($memory);$dex=[Text.Encoding]::ASCII.GetString($memory.ToArray());foreach($probe in @('data/CacheProbe','data/ProfileProbe','radio/RadioProbe','radio/RadioLockProbe','ui/DisplayProbe','ui/LifecycleProbe','ui/SoakProbe','ui/StyleProbe','data/ApiBenchmark')){if($dex.Contains('Llocal/mediacentre/client/'+$probe+';')){throw "Debug probe leaked into release: $probe"}}}
            finally{$stream.Dispose();$memory.Dispose()}
        }
    }finally{$zip.Dispose()}
}
Write-Output 'PASS: release excludes development CA; both APKs contain no native ABI libraries.'
