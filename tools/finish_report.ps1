# Opens the generated report in Microsoft Word, updates the table of contents,
# writes the real page numbers into the List of Figures and List of Tables, saves, and exports a PDF.
# Usage: powershell -File tools\finish_report.ps1 -Path <full path to .docx>
param([Parameter(Mandatory = $true)][string]$Path)

$ErrorActionPreference = 'Stop'
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
    $doc = $word.Documents.Open($Path)
    $doc.Repaginate()

    # Page number (as printed, honouring section restarts) of every figure and table caption.
    $pages = @{}
    foreach ($p in $doc.Paragraphs) {
        $t = $p.Range.Text.Trim()
        if ($t -match '^(Figure|Table) (\d+\.\d+):') {
            $pages["$($matches[1]) $($matches[2])"] = $p.Range.Information(1)
        }
    }

    foreach ($tbl in $doc.Tables) {
        $head = $tbl.Cell(1, 1).Range.Text
        if ($head -notmatch 'S\.No') { continue }
        for ($r = 2; $r -le $tbl.Rows.Count; $r++) {
            $key = ($tbl.Cell($r, 1).Range.Text -replace '[\r\a]', '').Trim()
            if ($pages.ContainsKey($key)) {
                $tbl.Cell($r, 3).Range.Text = [string]$pages[$key]
            }
        }
    }

    foreach ($toc in $doc.TablesOfContents) { $toc.Update() }
    $doc.Fields.Update() | Out-Null
    $doc.Save()
    $pdf = [System.IO.Path]::ChangeExtension($Path, '.pdf')
    $doc.SaveAs2([ref]$pdf, [ref]17)
    Write-Output "pages: $($doc.ComputeStatistics(2))"
    Write-Output "captions: $($pages.Count)"
    $doc.Close([ref]0)
}
finally {
    $word.Quit()
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
}
