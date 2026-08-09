Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap 16, 16
$hair = [System.Drawing.Color]::FromArgb(74, 48, 32)
$skin = [System.Drawing.Color]::FromArgb(224, 172, 105)
$eye  = [System.Drawing.Color]::FromArgb(34, 34, 68)
$coat = [System.Drawing.Color]::FromArgb(42, 59, 102)
$gold = [System.Drawing.Color]::FromArgb(212, 175, 55)

# 头发
for ($x = 5; $x -le 10; $x++) {
    $bmp.SetPixel($x, 1, $hair)
    $bmp.SetPixel($x, 2, $hair)
}
# 脸
for ($y = 3; $y -le 6; $y++) {
    for ($x = 5; $x -le 10; $x++) {
        $bmp.SetPixel($x, $y, $skin)
    }
}
$bmp.SetPixel(6, 4, $eye)
$bmp.SetPixel(9, 4, $eye)
# 制服身体
for ($y = 7; $y -le 14; $y++) {
    for ($x = 4; $x -le 11; $x++) {
        $bmp.SetPixel($x, $y, $coat)
    }
}
# 金色徽章
$bmp.SetPixel(7, 8, $gold)
$bmp.SetPixel(8, 8, $gold)

$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'src\main\resources\assets\noellesroles\textures\item\dialog_npc.png'
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "saved: $out"
