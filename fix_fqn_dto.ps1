$dir = "d:\Capstone\source\mkaix.healthsyncbe\src\main\java\com\g93\be"
$files = Get-ChildItem -Path $dir -Recurse -Filter *.java

foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw
    $pattern = '(?<!import\s+)com\.g93\.be\.dto\.([A-Z]\w*)'
    $matches = [regex]::Matches($content, $pattern)
    
    if ($matches.Count -gt 0) {
        $classesToImport = @()
        foreach ($m in $matches) {
            $classesToImport += $m.Groups[1].Value
        }
        $classesToImport = $classesToImport | Select-Object -Unique
        
        # Replace occurrences
        $newContent = [regex]::Replace($content, $pattern, '$1')
        
        # Add imports
        $importBlock = ""
        foreach ($cls in $classesToImport) {
            $importStmt = "import com.g93.be.dto.$cls;"
            if ($newContent -notmatch [regex]::Escape($importStmt)) {
                $importBlock += "$importStmt`r`n"
            }
        }
        
        if ($importBlock -ne "") {
            # insert after package line
            $newContent = $newContent -replace '^(package\s+[^;]+;)', "`$1`r`n$importBlock"
        }
        
        Set-Content -Path $file.FullName -Value $newContent -Encoding UTF8
        Write-Host "Updated $($file.Name) with imports: $($classesToImport -join ', ')"
    }
}
