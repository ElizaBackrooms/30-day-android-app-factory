param(
    [string]$DayFolder = "",
    [string]$CommitMessage = "",
    [switch]$ExtractOnly
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

            if ($isAndroidBundle -and $nameMatchesSlug) {
                $targetProjectDir = Join-Path $ResolvedDayFolder "android_project"
                $tempExtractDir = Join-Path $RepoRoot ".private\tmp_extract_$([Guid]::NewGuid().ToString('N'))"
                New-Item -ItemType Directory -Path $tempExtractDir -Force | Out-Null

                try {
                    $tempRootFull = [System.IO.Path]::GetFullPath($tempExtractDir)
                    if (-not $tempRootFull.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
                        $tempRootFull += [System.IO.Path]::DirectorySeparatorChar
                    }

                    # Safe Zip extraction with Zip Slip path-traversal protection
                    $zipArchive = [System.IO.Compression.ZipFile]::OpenRead($zip.FullName)
                    try {
                        foreach ($entry in $zipArchive.Entries) {
                            $destPath = [System.IO.Path]::GetFullPath((Join-Path $tempExtractDir $entry.FullName))
                            if (-not $destPath.StartsWith($tempRootFull, [System.StringComparison]::OrdinalIgnoreCase)) {
                                throw "Blocked unsafe Zip Slip path traversal entry '$($entry.FullName)' in '$($zip.Name)'."
                            }
                            if ([string]::IsNullOrEmpty($entry.Name)) {
                                New-Item -ItemType Directory -Path $destPath -Force | Out-Null
                            } else {
                                $destParent = Split-Path -Parent $destPath
                                if (-not (Test-Path $destParent)) {
                                    New-Item -ItemType Directory -Path $destParent -Force | Out-Null
                                }
                                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destPath, $true)
                            }
                        }
                    } finally {
                        $zipArchive.Dispose()
                    }

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

                    # Remove local/build/credential artifacts that must never enter git
                    $blockedNames = @(
                        "local.properties", "keystore.properties", "signing.properties", "secrets.properties",
                        "credentials.json", "google-services.json", "GoogleService-Info.plist",
                        ".env", ".DS_Store", "Thumbs.db", "desktop.ini"
                    )
                    $blockedExts = @(".jks", ".keystore", ".p12", ".pfx", ".key", ".pem", ".ppk", ".cer", ".crt", ".der", ".mobileprovision", ".secret")

                    Get-ChildItem -Path $targetProjectDir -Recurse -Force -ErrorAction SilentlyContinue |
                        Where-Object {
                            $_.Name -in $blockedNames -or
                            $_.Extension.ToLowerInvariant() -in $blockedExts -or
                            $_.Name -like ".env.*" -or
                            $_.Name -like "id_rsa*" -or
                            $_.Name -like "id_ed25519*" -or
                            $_.Name -like "*service-account*.json" -or
                            $_.Name -like "*firebase-adminsdk*.json"
                        } | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

                    # Scrub Windows/macOS/Linux home directory paths inside extracted text files (UTF-8 without BOM)
                    $textExtensions = @(".kt", ".kts", ".java", ".xml", ".gradle", ".properties", ".md", ".txt", ".json", ".pro", ".yml", ".yaml")
                    $winPathPattern = '[A-Za-z]:(?:\\\\|\\|/)Users(?:\\\\|\\|/)[^\s"''`]+'
                    $macPathPattern = '/' + 'Users/' + '[^\s"''`]+'
                    $linuxPathPattern = '/' + 'home/' + '[^\s"''`]+'
                    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)

                    Get-ChildItem -Path $targetProjectDir -Recurse -File -ErrorAction SilentlyContinue |
                        Where-Object { $textExtensions -contains $_.Extension.ToLowerInvariant() } |
                        ForEach-Object {
                            $raw = Get-Content -Path $_.FullName -Raw -ErrorAction SilentlyContinue
                            if ($null -ne $raw) {
                                $sanitized = $raw -replace $winPathPattern, '<USER_HOME>'
                                $sanitized = $sanitized -replace $macPathPattern, '<USER_HOME>'
                                $sanitized = $sanitized -replace $linuxPathPattern, '<USER_HOME>'
                                if ($sanitized -ne $raw) {
                                    [System.IO.File]::WriteAllText($_.FullName, $sanitized, $utf8NoBom)
                                }
                            }
                        }

                    Write-Host "[OK] Extracted Android project bundle '$($zip.Name)' -> $targetProjectDir"

                    # Ensure Dependabot watches Gradle projects once at least one android_project exists
                    $dependabotFile = Join-Path $RepoRoot ".github\dependabot.yml"
                    if (Test-Path $dependabotFile) {
                        $dbRaw = Get-Content -Path $dependabotFile -Raw
                        if ($dbRaw -notmatch 'package-ecosystem:\s*"gradle"') {
                            $gradleBlock = "`n  - package-ecosystem: `"gradle`"`n    directories:`n      - `"/days/*/android_project`"`n      - `"/days/*/android_project/app`"`n    schedule:`n      interval: `"weekly`"`n    labels:`n      - `"security`"`n      - `"dependencies`"`n"
                            [System.IO.File]::WriteAllText($dependabotFile, ($dbRaw.TrimEnd() + "`n" + $gradleBlock), $utf8NoBom)
                        }
                    }
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

# 3. Run shared Security & OpSec Audit Gate
$auditScript = Join-Path $PSScriptRoot "security_audit.ps1"
& $auditScript
if (-not $? -or $LASTEXITCODE -ne 0) {
    exit 1
}

if ($ExtractOnly) {
    Write-Host "[OK] ExtractOnly mode complete. Run the security-overseer subagent and scripts/verify_day_attestation.ps1 -Action Write before publishing."
    exit 0
}

# 3b. Enforce mandatory pre-upload Security Overseer SHA-256 attestation
$attestationScript = Join-Path $PSScriptRoot "verify_day_attestation.ps1"
if ($DayFolder -ne "") {
    & $attestationScript -Action Verify -DayFolder $DayFolder
} else {
    & $attestationScript -Action Verify
}
if (-not $? -or $LASTEXITCODE -ne 0) {
    exit 1
}

# 4. Enforce safe public Git author identity and verify ElizaBackrooms GitHub CLI account
Invoke-GitChecked @("config", "user.name", "ElizaBackrooms") | Out-Null
Invoke-GitChecked @("config", "user.email", "ElizaBackrooms@users.noreply.github.com") | Out-Null

if (Get-Command gh -ErrorAction SilentlyContinue) {
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $switchOut = & gh auth switch --user ElizaBackrooms 2>&1
    $switchCode = $LASTEXITCODE
    $ErrorActionPreference = $prevEAP
    if ($switchCode -ne 0) {
        throw "Failed to switch active gh account to ElizaBackrooms: $($switchOut -join "`n")"
    }
}

# 5. Stage targeted changes (or all audited files when no -DayFolder specified), commit, and push
if ($DayFolder -ne "") {
    $pathsToStage = New-Object System.Collections.Generic.List[string]
    $pathsToStage.Add($DayFolder)
    foreach ($metaFile in @("tracker.json", "SECURITY_AUDIT_LOG.md", ".github/dependabot.yml")) {
        if (Test-Path (Join-Path $RepoRoot $metaFile)) {
            $pathsToStage.Add($metaFile)
        }
    }
    Invoke-GitChecked (@("add", "--") + $pathsToStage) | Out-Null
} else {
    Invoke-GitChecked @("add", "-A") | Out-Null
}

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
