$dir = "d:\Capstone\source\mkaix.healthsyncbe\src\main\java\com\g93\be"
$files = Get-ChildItem -Path $dir -Recurse -Filter *.java

$utf8NoBom = New-Object System.Text.UTF8Encoding $false

foreach ($file in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        Write-Host "Removing BOM from $($file.Name)"
        $content = [System.IO.File]::ReadAllText($file.FullName)
        [System.IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)
    }
}
