# Restarts Origin in the background for browser checks. Needs MySQL running and JDK 17+.
# Usage (from anywhere): powershell -File tools\qa\restart.ps1
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-26.0.2' }
$env:Path = "C:\Dev\apache-maven-3.9.9\bin;$env:JAVA_HOME\bin;$env:Path"
Set-Location $root

Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
Start-Sleep 1

mvn -q -B compile dependency:build-classpath "-Dmdep.outputFile=target\classpath.txt" "-Dmdep.includeScope=compile" 2>&1 | Where-Object { $_ -match 'ERROR' } | Select-Object -First 5
$deps = Get-Content target\classpath.txt -Raw
$log = Join-Path $env:TEMP 'origin-run.log'
$err = Join-Path $env:TEMP 'origin-run.err'
Set-Content -Path $log -Value ''
Set-Content -Path $err -Value ''
Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList @('-cp', "target\classes;$deps", 'np.edu.origin.Launcher') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError $err
for ($i = 0; $i -lt 90; $i++) {
    Start-Sleep 1
    if (Select-String -Path $log -Pattern 'origin\] ready' -Quiet) { "up after $i s"; break }
}
Start-Sleep 5
Get-Content $err | Where-Object { $_ -match 'SEVERE|Exception|ERROR' } | Select-Object -First 8
