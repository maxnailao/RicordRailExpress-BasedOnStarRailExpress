param(
    [string]$Src,
    [string]$Dst,
    [string]$Pattern
)
$Src = (Resolve-Path $Src).Path
$Dst = (Resolve-Path $Dst).Path
$srcFiles = Get-ChildItem $Src -Recurse -File -Filter *.java | ForEach-Object { $_.FullName.Substring($Src.Length + 1) }
foreach ($rel in $srcFiles) {
    $base = ($rel -replace '\\', '/')
    $name = Split-Path $rel -Leaf
    if ($name -notmatch $Pattern) { continue }
    $dstFile = Join-Path $Dst $rel
    if (-not (Test-Path $dstFile)) {
        Write-Output "MISSING: $base"
        continue
    }
    # 内容比对（忽略 15 行许可证头）
    $a = (Get-Content (Join-Path $Src $rel) -Raw) -replace '(?s)^/\*.*?GPL.*?\*/\s*', ''
    $b = (Get-Content $dstFile -Raw) -replace '(?s)^/\*.*?GPL.*?\*/\s*', ''
    $a = ($a -replace "`r`n", "`n").Trim()
    $b = ($b -replace "`r`n", "`n").Trim()
    if ($a -ne $b) {
        Write-Output "DIFFERENT: $base"
    }
}
