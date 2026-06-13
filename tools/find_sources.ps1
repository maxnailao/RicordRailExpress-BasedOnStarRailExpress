$tempDir = "$env:TEMP\mc_class"
$outputFile = "$env:TEMP\mc_fishinghook.txt"

javap -p -c "$tempDir\FishingHook.class" > $outputFile 2>&1
Write-Host "Written to $outputFile"
Write-Host "File size: $((Get-Item $outputFile).Length) bytes"
