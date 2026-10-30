param([string]$ProjectRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing

function RoundedSquare([single]$x,[single]$y,[single]$size,[single]$radius) {
    $path=[Drawing.Drawing2D.GraphicsPath]::new()
    $d=2*$radius
    $path.AddArc($x,$y,$d,$d,180,90)
    $path.AddArc($x+$size-$d,$y,$d,$d,270,90)
    $path.AddArc($x+$size-$d,$y+$size-$d,$d,$d,0,90)
    $path.AddArc($x,$y+$size-$d,$d,$d,90,90)
    $path.CloseFigure()
    return $path
}

$source=[Drawing.Bitmap]::new(512,512)
$graphics=[Drawing.Graphics]::FromImage($source)
$graphics.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([Drawing.Color]::Transparent)
$background=RoundedSquare 16 16 480 108
$gradient=[Drawing.Drawing2D.LinearGradientBrush]::new([Drawing.Point]::new(48,36),[Drawing.Point]::new(470,490),[Drawing.Color]::FromArgb(21,51,95),[Drawing.Color]::FromArgb(29,106,188))
$graphics.FillPath($gradient,$background)

$roof=[Drawing.Pen]::new([Drawing.Color]::FromArgb(242,249,255),30)
$roof.StartCap=$roof.EndCap=[Drawing.Drawing2D.LineCap]::Round
$roof.LineJoin=[Drawing.Drawing2D.LineJoin]::Round
$house=[Drawing.Drawing2D.GraphicsPath]::new()
$house.AddLines([Drawing.Point[]]@([Drawing.Point]::new(104,250),[Drawing.Point]::new(256,119),[Drawing.Point]::new(408,250)))
$graphics.DrawPath($roof,$house)
$graphics.DrawLine($roof,133,247,133,390)
$graphics.DrawLine($roof,379,247,379,390)
$graphics.DrawLine($roof,133,390,379,390)

$cyan=[Drawing.Color]::FromArgb(120,215,255)
$radio=[Drawing.Pen]::new($cyan,22)
$radio.StartCap=$radio.EndCap=[Drawing.Drawing2D.LineCap]::Round
$graphics.DrawArc($radio,197,229,118,118,205,130)
$graphics.FillEllipse([Drawing.SolidBrush]::new($cyan),242,307,28,28)

$output=Join-Path $ProjectRoot 'android/app/src/main/res'
foreach($entry in @(@('mdpi',48),@('hdpi',72),@('xhdpi',96),@('xxhdpi',144),@('xxxhdpi',192))){
    $directory=Join-Path $output ('mipmap-'+$entry[0])
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $size=[int]$entry[1]
    $bitmap=[Drawing.Bitmap]::new($size,$size)
    $target=[Drawing.Graphics]::FromImage($bitmap)
    $target.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $target.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $target.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $target.DrawImage($source,0,0,$size,$size)
    $bitmap.Save((Join-Path $directory 'ic_launcher.png'),[Drawing.Imaging.ImageFormat]::Png)
    $target.Dispose();$bitmap.Dispose()
}
$house.Dispose();$roof.Dispose();$radio.Dispose();$gradient.Dispose();$background.Dispose();$graphics.Dispose();$source.Dispose()
