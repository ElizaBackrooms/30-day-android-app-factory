param(
    [string]$DayFolder = "",
    [string]$CommitMessage = ""
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

function Invoke-GitChecked {
    param([string[]]$GitArgs)
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & git -C $RepoRoot @GitArgs 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prevEAP
    }
    if ($code -ne 0) {
        throw "git $($GitArgs -join ' ') failed: $($output -join "`n")"
    }
    return $output
}

# 1. Resolve target day folder (explicit or newest days/day_*)
$ResolvedDayFolder = $null
if ($DayFolder -ne "") {
    if ([System.IO.Path]::IsPathRooted($DayFolder)) {
        $ResolvedDayFolder = $DayFolder
    } else {
        $ResolvedDayFolder = Join-Path $RepoRoot $DayFolder
    }
} else {
    $daysDir = Join-Path $RepoRoot "days"
    if (Test-Path $daysDir) {
        $latest = Get-ChildItem -Path $daysDir -Directory -Filter "day_*" | Sort-Object Name -Descending | Select-Object -First 1
        if ($latest) {
            $ResolvedDayFolder = $latest.FullName
        }
    }
}

# 2. Ingest recently downloaded Google AI Studio Android bundle if present
if ($ResolvedDayFolder -and (Test-Path $ResolvedDayFolder)) {
    $downloadsDir = Join-Path $env:USERPROFILE "Downloads"
    if (Test-Path $downloadsDir) {
        $cutoff = (Get-Date).AddMinutes(-30)
        $dayLeaf = Split-Path -Leaf $ResolvedDayFolder
        $slugTokens = ($dayLeaf -replace '^day_\d+_', '') -split '_' | Where-Object { $_.Length -ge 3 }

        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $recentZips = Get-ChildItem -Path $downloadsDir -Filter "*.zip" -File -ErrorAction SilentlyContinue |
            Where-Object { $_.LastWriteTime -ge $cutoff } |
            Sort-Object LastWriteTime -Descending

        foreach ($zip in $recentZips) {
            $isAndroidBundle = $false
            $nameMatchesSlug = $false
            foreach ($tok in $slugTokens) {
                if ($zip.BaseName -match [regex]::Escape($tok)) {
                    $nameMatchesSlug = $true
                    break
                }
            }

            try {
                $archive = [System.IO.Compression.ZipFile]::OpenRead($zip.FullName)
                try {
                    $entryNames = $archive.Entries | ForEach-Object { $_.FullName }
                    $hasGradle = ($entryNames | Where-Object { $_ -match '(^|/)(build\.gradle(\.kts)?|settings\.gradle(\.kts)?)$' }).Count -gt 0
                    $hasManifest = ($entryNames | Where-Object { $_ -match 'AndroidManifest\.xml$' }).Count -gt 0
                    if ($hasGradle -and $hasManifest) {
                        $isAndroidBundle = $true
                    }
                } finally {
                    $archive.Dispose()
                }
            } catch {
                continue
            }

            if ($isAndroidBundle -and ($nameMatchesSlug -or $DayFolder -ne "")) {
                $targetProjectDir = Join-Path $ResolvedDayFolder "android_project"
                $tempExtractDir = Join-Path $RepoRoot ".private\tmp_extract_$([Guid]::NewGuid().ToString('N'))"
                New-Item -ItemType Directory -Path $tempExtractDir -Force | Out-Null

                try {
                    [System.IO.Compression.ZipFile]::ExtractToDirectory($zip.FullName, $tempExtractDir)

                    $extractedTopDirs = Get-ChildItem -Path $tempExtractDir -Directory
                    $extractedTopFiles = Get-ChildItem -Path $tempExtractDir -File
                    $sourceRoot = $tempExtractDir
                    if ($extractedTopDirs.Count -eq 1 -and $extractedTopFiles.Count -eq 0) {
                        $sourceRoot = $extractedTopDirs[0].FullName
                    }

                    if (Test-Path $targetProjectDir) {
                        Remove-Item -Path $targetProjectDir -Recurse -Force
                    }
                    New-Item -ItemType Directory -Path $targetProjectDir -Force | Out-Null
                    Copy-Item -Path (Join-Path $sourceRoot "*") -Destination $targetProjectDir -Recurse -Force

                    # Remove local/build artifacts that must never enter git
                    Get-ChildItem -Path $targetProjectDir -Recurse -Force -ErrorAction SilentlyContinue |
                        Where-Object {
                            $_.Name -in @("local.properties", "keystore.properties", ".DS_Store", "Thumbs.db") -or
                            $_.Extension -in @(".jks", ".keystore", ".p12", ".pfx", ".key", ".pem")
                        } | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

                    # Scrub any Windows/macOS user paths inside extracted text files
                    $textExtensions = @(".kt", ".kts", ".java", ".xml", ".gradle", ".properties", ".md", ".txt", ".json", ".pro", ".yml", ".yaml")
                    $userPattern = [regex]::Escape($env:USERNAME)
                    $winPathPattern = '[A-Za-z]:\\' + 'Users\\' + '[^\\\s"''`]+'
                    $macPathPattern = '/' + 'Users/' + '[^/\s"''`]+'

                    Get-ChildItem -Path $targetProjectDir -Recurse -File -ErrorAction SilentlyContinue |
                        Where-Object { $textExtensions -contains $_.Extension.ToLowerInvariant() } |
                        ForEach-Object {
                            $raw = Get-Content -Path $_.FullName -Raw -ErrorAction SilentlyContinue
                            if ($null -ne $raw) {
                                $sanitized = $raw -replace $winPathPattern, '<USER_HOME>'
                                $sanitized = $sanitized -replace $macPathPattern, '<USER_HOME>'
                                if ($env:USERNAME.Length -ge 3) {
                                    $sanitized = [regex]::Replace($sanitized, "\b$userPattern\b", 'developer', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
                                }
                                if ($sanitized -ne $raw) {
                                    Set-Content -Path $_.FullName -Value $sanitized -NoNewline -Encoding UTF8
                                }
                            }
                        }

                    Write-Host "[OK] Extracted Android project bundle '$($zip.Name)' -> $targetProjectDir"
                } finally {
                    if (Test-Path $tempExtractDir) {
                        Remove-Item -Path $tempExtractDir -Recurse -Force -ErrorAction SilentlyContinue
                    }
                }
                break
            }
        }
    }
}

# 3. Security & PII Audit Gate across all tracked + untracked non-ignored files
$candidateFiles = & git -C $RepoRoot ls-files --cached --others --exclude-standard
if ($LASTEXITCODE -ne 0) {
    throw "Failed to enumerate repository files via git ls-files."
}

$forbiddenExtensions = @(".jks", ".keystore", ".p12", ".pfx", ".key", ".pem")
$forbiddenNames = @("local.properties", "keystore.properties", ".env", "google-services.json")
$binaryExtensions = @(".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".ttf", ".otf", ".jar", ".so", ".zip", ".apk", ".aab", ".woff", ".woff2")

$winUserPathRegex = '[A-Za-z]:\\' + 'Users\\' + '[^\\\s"''`]+'
$macUserPathRegex = '/' + 'Users/' + '[^/\s"''`]+'
$studioUrlRegex = 'aistudio\.google\.com' + '/apps/'
$secretRegex = '(ghp_' + '[0-9A-Za-z_]{20,}|gho_' + '[0-9A-Za-z_]{20,}|github_pat_' + '[0-9A-Za-z_]{20,}|AIza[0-9A-Za-z\-_]{35}|sk-[0-9A-Za-z]{20,}|' + '-----BEGIN [A-Z ]+PRIVATE KEY' + '-----)'
$cleartextRegex = 'usesCleartextTraffic\s*=\s*"' + 'true"'
$usernameRegex = if ($env:USERNAME.Length -ge 3) { "(?<![A-Za-z0-9_])$([regex]::Escape($env:USERNAME))(?![A-Za-z0-9_])" } else { "" }

$violations = New-Object System.Collections.Generic.List[string]

foreach ($relPath in $candidateFiles) {
    if ([string]::IsNullOrWhiteSpace($relPath)) { continue }
    $fullPath = Join-Path $RepoRoot $relPath
    if (-not (Test-Path $fullPath -PathType Leaf)) { continue }

    $fileName = [System.IO.Path]::GetFileName($fullPath)
    $ext = [System.IO.Path]::GetExtension($fullPath).ToLowerInvariant()

    if ($forbiddenExtensions -contains $ext -or $forbiddenNames -contains $fileName -or $fileName -like ".env.*") {
        $violations.Add("${relPath}: Forbidden credential/keystore/env file detected.")
        continue
    }

    if ($binaryExtensions -contains $ext) { continue }

    $lines = Get-Content -Path $fullPath -ErrorAction SilentlyContinue
    if ($null -eq $lines) { continue }

    $lineNum = 0
    foreach ($line in $lines) {
        $lineNum++
        if ($line -match $winUserPathRegex) {
            $violations.Add("${relPath}:${lineNum}: Windows user directory path exposed.")
        }
        if ($line -match $macUserPathRegex) {
            $violations.Add("${relPath}:${lineNum}: macOS user directory path exposed.")
        }
        if ($line -match $studioUrlRegex) {
            $violations.Add("${relPath}:${lineNum}: Private Google AI Studio URL exposed.")
        }
        if ($line -match $secretRegex) {
            $violations.Add("${relPath}:${lineNum}: Potential API token or private key detected.")
        }
        if (($ext -in @(".xml", ".gradle", ".kts")) -and ($line -match $cleartextRegex)) {
            $violations.Add("${relPath}:${lineNum}: Insecure AndroidManifest usesCleartextTraffic=true detected.")
        }
        if ($usernameRegex -ne "" -and ([regex]::IsMatch($line, $usernameRegex, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase))) {
            $violations.Add("${relPath}:${lineNum}: Local OS username detected.")
        }
    }
}

if ($violations.Count -gt 0) {
    Write-Error ("Security audit FAILED with $($violations.Count) violation(s):`n - " + ($violations -join "`n - "))
    exit 1
}

Write-Host "[OK] Security & OpSec audit passed across $($candidateFiles.Count) file(s)."

# 4. Enforce safe public Git author identity
Invoke-GitChecked @("config", "user.name", "ElizaBackrooms") | Out-Null
Invoke-GitChecked @("config", "user.email", "ElizaBackrooms@users.noreply.github.com") | Out-Null

# Ensure gh CLI active account is ElizaBackrooms if gh is installed
if (Get-Command gh -ErrorAction SilentlyContinue) {
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & gh auth switch --user ElizaBackrooms 2>$null | Out-Null
    } finally {
        $ErrorActionPreference = $prevEAP
    }
}

# 5. Stage, commit if changed, and push to origin main
Invoke-GitChecked @("add", "-A") | Out-Null

& git -C $RepoRoot diff --cached --quiet
$hasStagedChanges = ($LASTEXITCODE -ne 0)

if ($hasStagedChanges) {
    if ([string]::IsNullOrWhiteSpace($CommitMessage)) {
        $CommitMessage = "chore: publish daily android app update ($(Get-Date -Format 'yyyy-MM-dd HH:mm'))"
    }
    Invoke-GitChecked @("commit", "-m", $CommitMessage) | Out-Null
    Write-Host "[OK] Committed: $CommitMessage"
} else {
    Write-Host "[INFO] No new changes to commit."
}

$prevEAP = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$aheadCount = (& git -C $RepoRoot rev-list --count "origin/main..HEAD" 2>$null)
$revListCode = $LASTEXITCODE
$ErrorActionPreference = $prevEAP

if ($revListCode -ne 0 -or [int]$aheadCount -gt 0 -or $hasStagedChanges) {
    Invoke-GitChecked @("push", "-u", "origin", "main") | Out-Null
    Write-Host "[OK] Pushed main to origin."
} else {
    Write-Host "[OK] origin/main is already up to date."
}
