param([string]$Lang = "zh_cn")
$src = "StarRailExpress\src\main\resources\assets\starrailexpress\lang\$Lang.json"
$dst = "src\main\resources\assets\starrailexpress\lang\$Lang.json"
$a = Get-Content $src -Raw -Encoding UTF8 | ConvertFrom-Json
$b = Get-Content $dst -Raw -Encoding UTF8 | ConvertFrom-Json
$dstKeys = $b.PSObject.Properties.Name
foreach ($p in $a.PSObject.Properties) {
    if ($p.Name -like "replay.*" -or $p.Name -like "sre.replay.*") {
        if ($dstKeys -notcontains $p.Name) {
            Write-Output ("MISSING`t" + $p.Name + "`t" + $p.Value)
        }
    }
}
