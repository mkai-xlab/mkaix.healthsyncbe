$srcDir = "d:\Capstone\source\mkaix.healthsyncbe\src\main\java\com\g93\be"
$folders = @("$srcDir\controller", "$srcDir\service\impl")

$counter = 1
Write-Host "No`tModule Name`tMethod Name`tSheet Name`tDescription`tPre-Condition"

foreach ($folder in $folders) {
    if (Test-Path $folder) {
        $files = Get-ChildItem -Path $folder -Filter *.java
        foreach ($file in $files) {
            $className = $file.BaseName
            $content = Get-Content $file.FullName -Raw
            
            # Match public methods
            # public [type] [methodName](...)
            $pattern = '(?m)^\s*public\s+(?:<[^>]+>\s+)?([\w<>,\[\]\s]+)\s+([a-zA-Z0-9_]+)\s*\([^)]*\)\s*(?:throws\s+[\w,\s]+)?\s*\{'
            $matches = [regex]::Matches($content, $pattern)
            
            foreach ($m in $matches) {
                $methodName = $m.Groups[2].Value
                if ($methodName -match '^(get|set|toString|equals|hashCode)$' -or $methodName -eq $className) {
                    continue
                }
                
                $sheetName = $methodName
                if ($folder -match "controller") {
                    $sheetName = "$className.$methodName"
                    if ($sheetName.Length -gt 31) {
                        $sheetName = $sheetName.Substring(0, 31)
                    }
                }
                
                Write-Host "$counter`t$className`t$methodName`t$sheetName`t`t"
                $counter++
            }
        }
    }
}
