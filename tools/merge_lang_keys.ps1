param([string]$Lang = "zh_cn")
$src = "StarRailExpress\src\main\resources\assets\starrailexpress\lang\$Lang.json"
$dst = "src\main\resources\assets\starrailexpress\lang\$Lang.json"
$srcLines = Get-Content $src -Encoding UTF8
$a = Get-Content $src -Raw -Encoding UTF8 | ConvertFrom-Json
$b = Get-Content $dst -Raw -Encoding UTF8 | ConvertFrom-Json
$dstKeys = $b.PSObject.Properties.Name

# 收集缺失键（保持内部文件中的原始行文本）
$missingLines = @()
foreach ($line in $srcLines) {
    if ($line -match '^\s*"((?:sre\.)?replay[^"]+)"\s*:') {
        $key = $Matches[1]
        if ($dstKeys -notcontains $key) {
            $missingLines += $line
            $dstKeys += $key
        }
    }
}
if ($missingLines.Count -eq 0) { Write-Output "no missing keys"; return }

$dstLines = [System.Collections.Generic.List[string]]::new()
$dstLines.AddRange((Get-Content $dst -Encoding UTF8))
$anchorIdx = -1
for ($i = $dstLines.Count - 1; $i -ge 0; $i--) {
    if ($dstLines[$i] -match '"(sre\.)?replay') { $anchorIdx = $i; break }
}
if ($anchorIdx -lt 0) { $anchorIdx = $dstLines.Count - 2 }
if (-not $dstLines[$anchorIdx].TrimEnd().EndsWith(',')) {
    $dstLines[$anchorIdx] = $dstLines[$anchorIdx].TrimEnd() + ','
}
for ($j = 0; $j -lt $missingLines.Count; $j++) {
    $l = $missingLines[$j].TrimEnd()
    # 插入块内除最后一行外都保证有逗号
    if ($j -lt $missingLines.Count - 1 -and -not $l.EndsWith(',')) { $l = $l + ',' }
    if ($j -eq $missingLines.Count - 1 -and $l.EndsWith(',')) {
        # 若锚点后还有内容，则保留逗号；这里锚点是最后一个 replay 键，其后可能还有其他键
        # 保守处理：保留逗号（锚点行可能不是文件最后一个键）
    }
    $dstLines.Insert($anchorIdx + 1 + $j, $l)
}
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines((Resolve-Path $dst).Path, $dstLines, $utf8NoBom)
Write-Output ("inserted " + $missingLines.Count + " keys after line " + ($anchorIdx + 1))
