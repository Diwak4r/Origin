# Exports every slide of a .pptx to PNG files through PowerPoint, for visual review.
# Usage: powershell -File tools\export_slides.ps1 -Path <full .pptx path> -Out <folder>
param([Parameter(Mandatory = $true)][string]$Path, [Parameter(Mandatory = $true)][string]$Out)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force $Out | Out-Null
$app = New-Object -ComObject PowerPoint.Application
try {
    $pres = $app.Presentations.Open($Path, $true, $false, $false)   # read-only, no window
    $i = 1
    foreach ($s in $pres.Slides) {
        $s.Export((Join-Path $Out ("s{0:D2}.png" -f $i)), "PNG", 1600, 900)
        $i++
    }
    Write-Output "exported $($pres.Slides.Count) slides"
    $pres.Close()
}
finally {
    $app.Quit()
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($app) | Out-Null
}
