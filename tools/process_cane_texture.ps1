# 处理导盲杖素材图：白底转透明 + 最近邻缩放，生成效果图标与物品贴图
# 注意：Windows PowerShell 5.1 无 Math.Clamp，且脚本内避免中文字面量（通过通配符定位源文件）
Add-Type -AssemblyName System.Drawing

$srcDir = Join-Path $env:APPDATA 'QoderCN\SharedClientCache\cache\images\552c74ed'
# 按哈希后缀精确定位原始 32x32 素材（避免误取历史输出）
$srcFile = Get-ChildItem -Path $srcDir -Filter '*-f73a5457.png' | Select-Object -First 1
if ($null -eq $srcFile) {
    Write-Error 'source png not found'
    exit 1
}
$src = $srcFile.FullName
$effectOut = 'src\main\resources\assets\noellesroles\textures\mob_effect\blindness_sickness.png'
$itemOut = 'src\main\resources\assets\noellesroles\textures\item\guidance_cane.png'

function Clamp-Int([double]$v) {
    if ($v -lt 0) { return 0 }
    if ($v -gt 255) { return 255 }
    return [int]$v
}

function Convert-CaneImage([string]$source, [string]$target) {
    # 先复制到 ASCII 临时路径，规避中文路径在部分 API 下的编码问题
    $tmp = Join-Path $env:TEMP 'blindness_cane_src.png'
    Copy-Item -LiteralPath $source -Destination $tmp -Force
    # 保留原始分辨率（细线条缩放会丢失），仅做颜色反转：深色线条 → 白色，alpha 不变
    $resized = [System.Drawing.Bitmap]::new($tmp)
    for ($y = 0; $y -lt $resized.Height; $y++) {
        for ($x = 0; $x -lt $resized.Width; $x++) {
            $px = $resized.GetPixel($x, $y)
            if ($px.A -lt 10) {
                $resized.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
            } else {
                $resized.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($px.A, 255, 255, 255))
            }
        }
    }

    $dir = Split-Path $target -Parent
    if (!(Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $resized.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
    $resized.Dispose()
    Write-Output ('written: ' + $target)
}

Convert-CaneImage $src $effectOut
Convert-CaneImage $src $itemOut
