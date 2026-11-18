param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("Write", "Verify")]
    [string]$Action,

    [string]$DayFolder = "",
    [string]$Summary = "",
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot

$textExtensions = @(".kt", ".kts", ".java", ".xml", ".gradle", ".properties", ".md", ".txt", ".json", ".pro", ".yml", ".yaml", ".ps1")

function Get-Sha256Hex([byte[]]$bytes) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hash = $sha.ComputeHash($bytes)
        return ([System.BitConverter]::ToString($hash) -replace '-', '').ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

function Get-CanonicalFileHash([string]$fullPath) {
    $ext = [System.IO.Path]::GetExtension($fullPath).ToLowerInvariant()
    if ($textExtensions -contains $ext) {
        $text = [System.IO.File]::ReadAllText($fullPath) -replace "`r`n", "`n"
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($text)
        return Get-Sha256Hex $bytes
    }
    $rawBytes = [System.IO.File]::ReadAllBytes($fullPath)
    return Get-Sha256Hex $rawBytes
}

function Get-DayManifest([string]$dayRelPath) {
    $normalizedDayRel = ($dayRelPath -replace '\\', '/').TrimEnd('/')
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $files = & git -C $RepoRoot ls-files --cached --others --exclude-standard -- "$normalizedDayRel/" 2>&1
    $code = $LASTEXITCODE
    $ErrorActionPreference = $prevEAP
    if ($code -ne 0) {
        throw "Failed to list files under $normalizedDayRel`: $($files -join "`n")"
    }

    $entries = New-Object System.Collections.Generic.List[string]
    $sorted = $files |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        ForEach-Object { $_ -replace '\\', '/' } |
        Where-Object { $_ -notmatch '(^|/)SECURITY_REVIEW\.md$' } |
        Sort-Object -Unique

    foreach ($rel in $sorted) {
        $abs = Join-Path $RepoRoot $rel
        if (Test-Path $abs -PathType Leaf) {
            $h = Get-CanonicalFileHash $abs
            $entries.Add("$h  $rel")
        }
    }

    $manifestText = ($entries -join "`n")
    $manifestHash = Get-Sha256Hex ([System.Text.Encoding]::UTF8.GetBytes($manifestText))
    return [PSCustomObject]@{
        ManifestHash = $manifestHash
        Entries      = @($entries)
    }
}

function Resolve-DayRelative([string]$folderArg) {
    $full = if ([System.IO.Path]::IsPathRooted($folderArg)) {
        [System.IO.Path]::GetFullPath($folderArg)
    } else {
        [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $folderArg))
    }
    $rootFull = [System.IO.Path]::GetFullPath($RepoRoot)
    if (-not $full.StartsWith($rootFull, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "DayFolder '$folderArg' must be inside repository root."
    }
    $rel = $full.Substring($rootFull.Length).TrimStart('\', '/') -replace '\\', '/'
    return $rel
}

if ($Action -eq "Write") {
    if ([string]::IsNullOrWhiteSpace($DayFolder)) {
        throw "-DayFolder is required when -Action Write is specified."
    }
    if ([string]::IsNullOrWhiteSpace($Summary)) {
        throw "-Summary describing the Security Overseer review findings is required when -Action Write is specified."
    }

    $dayRel = Resolve-DayRelative $DayFolder
    $dayFull = Join-Path $RepoRoot $dayRel
    if (-not (Test-Path $dayFull -PathType Container)) {
        throw "Day folder not found: $dayRel"
    }

    # Static & OpSec audit must pass before signing off
    $auditScript = Join-Path $PSScriptRoot "security_audit.ps1"
    & $auditScript -Quiet
    if ($LASTEXITCODE -ne 0) {
        throw "Cannot write Security Overseer sign-off while security_audit.ps1 fails."
    }

    $manifest = Get-DayManifest $dayRel
    if ($manifest.Entries.Count -eq 0) {
        throw "No auditable files found inside $dayRel."
    }

    $dayLeaf = Split-Path -Leaf $dayFull
    $timestampUtc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    $cleanSummary = ($Summary -replace '\r?\n', ' ').Trim()

    $reviewLines = @(
        "# Security Overseer Sign-Off - $dayLeaf",
        "",
        "- **Day:** ``$dayLeaf``",
        "- **Reviewer:** ``security-overseer``",
        "- **Verdict:** ``PASS``",
        "- **Privacy_OptIn_Verified:** ``true``",
        "- **AppSec_OpSec_Verified:** ``true``",
        "- **Timestamp_UTC:** ``$timestampUtc``",
        "- **Manifest_SHA256:** ``$($manifest.ManifestHash)``",
        "- **Audit_Summary:** $cleanSummary",
        "",
        "## Verified File Manifest (SHA-256)",
        "``````text"
    ) + $manifest.Entries + @(
        "``````",
        ""
    )

    $reviewPath = Join-Path $dayFull "SECURITY_REVIEW.md"
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($reviewPath, ($reviewLines -join "`n"), $utf8NoBom)

    if (-not $Quiet) {
        Write-Host "[OK] Wrote Security Overseer sign-off -> $dayRel/SECURITY_REVIEW.md (SHA-256: $($manifest.ManifestHash))"
    }
    exit 0
}

# Verify mode
$targets = New-Object System.Collections.Generic.List[string]
if (-not [string]::IsNullOrWhiteSpace($DayFolder)) {
    $targets.Add((Resolve-DayRelative $DayFolder))
} else {
    $daysRoot = Join-Path $RepoRoot "days"
    if (Test-Path $daysRoot) {
        Get-ChildItem -Path $daysRoot -Directory -Filter "day_*" | Sort-Object Name | ForEach-Object {
            $dayName = $_.Name
            $hasAndroidProject = Test-Path (Join-Path $_.FullName "android_project")
            $isPrePolicyPrototype = ($dayName -match '^day_0[12]_') -and (-not $hasAndroidProject)
            if (-not $isPrePolicyPrototype) {
                $targets.Add("days/$dayName")
            }
        }
    }
}

$errors = New-Object System.Collections.Generic.List[string]
foreach ($dayRel in $targets) {
    $dayFull = Join-Path $RepoRoot $dayRel
    if (-not (Test-Path $dayFull -PathType Container)) { continue }

    $reviewPath = Join-Path $dayFull "SECURITY_REVIEW.md"
    if (-not (Test-Path $reviewPath -PathType Leaf)) {
        $errors.Add("${dayRel}: Missing mandatory SECURITY_REVIEW.md from Security Overseer before upload.")
        continue
    }

    $reviewText = Get-Content -Path $reviewPath -Raw
    if ($reviewText -notmatch '\*\*Verdict:\*\*\s*`PASS`') {
        $errors.Add("${dayRel}/SECURITY_REVIEW.md: Verdict is not PASS.")
        continue
    }
    if ($reviewText -notmatch '\*\*Privacy_OptIn_Verified:\*\*\s*`true`') {
        $errors.Add("${dayRel}/SECURITY_REVIEW.md: Privacy_OptIn_Verified must be true.")
        continue
    }
    if ($reviewText -notmatch '\*\*AppSec_OpSec_Verified:\*\*\s*`true`') {
        $errors.Add("${dayRel}/SECURITY_REVIEW.md: AppSec_OpSec_Verified must be true.")
        continue
    }
    if ($reviewText -notmatch '\*\*Manifest_SHA256:\*\*\s*`([a-f0-9]{64})`') {
        $errors.Add("${dayRel}/SECURITY_REVIEW.md: Missing or malformed Manifest_SHA256.")
        continue
    }

    $recordedHash = $Matches[1]
    $currentManifest = Get-DayManifest $dayRel
    if ($currentManifest.ManifestHash -ne $recordedHash) {
        $errors.Add("${dayRel}/SECURITY_REVIEW.md: File hash mismatch (files changed since last Security Overseer review; expected $recordedHash, got $($currentManifest.ManifestHash)). Re-run security-overseer audit.")
    }
}

if ($errors.Count -gt 0) {
    if (-not $Quiet) {
        Write-Error ("Security Overseer attestation verification FAILED:`n - " + ($errors -join "`n - "))
    } else {
        Write-Output ($errors -join "; ")
    }
    exit 1
}

if (-not $Quiet) {
    Write-Host "[OK] Security Overseer attestation verified for $($targets.Count) day folder(s)."
}
exit 0
