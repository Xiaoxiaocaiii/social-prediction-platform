$ErrorActionPreference = "Stop"

$roots = @(
    (Join-Path $PSScriptRoot "..\src\main\resources\static"),
    (Join-Path $PSScriptRoot "..\docs")
)

$files = foreach ($root in $roots) {
    if (-not (Test-Path $root)) {
        continue
    }
    Get-ChildItem -Path $root -Recurse -File |
        Where-Object {
            $_.Extension -in @(".css", ".html", ".js") -and
            $_.FullName -notmatch "[\\/]lib[\\/]echarts\.min\.js$"
        }
}

$rules = @(
    @{
        Name = "dimension media query"
        Pattern = '@media\s+(?!\(\s*prefers-reduced-motion\s*:)'
    },
    @{
        Name = "viewport configuration"
        Pattern = '<meta\s+[^>]*name\s*=\s*[^>]*viewport'
    },
    @{
        Name = "touch or device branch"
        Pattern = '(?i)\b(?:touchstart|touchmove|touchend|ontouch|pointer\s*:\s*coarse|matchMedia\s*\([^)]*(?:width|orientation|pointer)|innerWidth|outerWidth|screen\.width)\b'
    },
    @{
        Name = "mobile keywords"
        Pattern = '(?i)\b(?:mobile|tablet|responsive)\b|\u79fb\u52a8\u7aef|\u624b\u673a|\u5e73\u677f|\u54cd\u5e94\u5f0f'
    }
)

$violations = foreach ($file in $files) {
    $content = Get-Content -Raw -LiteralPath $file.FullName
    foreach ($rule in $rules) {
        if ($content -match $rule.Pattern) {
            [PSCustomObject]@{
                File = $file.FullName
                Rule = $rule.Name
            }
        }
    }
}

if ($violations) {
    Write-Host "Desktop-only policy check failed:" -ForegroundColor Red
    $violations | ForEach-Object {
        Write-Host (" - {0}: {1}" -f $_.File, $_.Rule)
    }
    exit 1
}

Write-Host ("Desktop-only policy check passed ({0} source files scanned)." -f @($files).Count) -ForegroundColor Green
