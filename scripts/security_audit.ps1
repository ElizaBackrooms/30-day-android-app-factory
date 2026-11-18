param(
    [switch]$Quiet,
    [switch]$JsonOutput
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot

$prevEAP = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$candidateFiles = & git -C $RepoRoot ls-files --cached --others --exclude-standard 2>&1
$lsCode = $LASTEXITCODE
$ErrorActionPreference = $prevEAP
if ($lsCode -ne 0) {
    throw "Failed to enumerate repository files via git ls-files: $($candidateFiles -join "`n")"
}

$forbiddenExtensions = @(".jks", ".keystore", ".p12", ".pfx", ".key", ".pem", ".ppk", ".cer", ".crt", ".der", ".mobileprovision", ".secret")
$forbiddenNames = @(
    "local.properties",
    "keystore.properties",
    "signing.properties",
    "secrets.properties",
    "credentials.json",
    "google-services.json",
    "GoogleService-Info.plist",
    "local_studio_urls.json",
    ".env"
)
$binaryExtensions = @(".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".ttf", ".otf", ".jar", ".so", ".zip", ".apk", ".aab", ".woff", ".woff2")

# Construct detection regexes dynamically so this script never triggers itself
$winUserPathRegex = '[A-Za-z]:(?:\\\\|\\|/)Users(?:\\\\|\\|/)[^\s"''`]+'
$macUserPathRegex = '/' + 'Users/' + '[^\s"''`]+'
$linuxUserPathRegex = '/' + 'home/' + '[^\s"''`]+'
$studioUrlRegex = '(aistudio\.google\.com|ai\.studio)' + '/apps/'
$secretRegex = '((?<![A-Za-z0-9_])ghp_' + '[0-9A-Za-z_]{20,}|(?<![A-Za-z0-9_])gho_' + '[0-9A-Za-z_]{20,}|(?<![A-Za-z0-9_])github_pat_' + '[0-9A-Za-z_]{20,}|(?<![A-Za-z0-9_])AIza[0-9A-Za-z\-_]{35}|(?<![A-Za-z0-9_])sk-(?:proj-|ant-)?[0-9A-Za-z_-]{20,}|(?<![A-Za-z0-9_])sk_(?:live|test)_[0-9A-Za-z]{16,}|(?<![A-Za-z0-9_])AKIA[0-9A-Z]{16}|(?<![A-Za-z0-9_])xox[baprs]-[0-9A-Za-z-]{10,}|(?<![A-Za-z0-9_])GOCSPX-[0-9A-Za-z_-]{20,}|' + '-----BEGIN ([A-Z0-9 ]+ )?PRIVATE KEY' + '-----)'
$cleartextRegex = 'usesCleartextTraffic\s*=\s*"' + 'true"'
$cleartextFalseRegex = 'android:usesCleartextTraffic\s*=\s*"false"'
$allowBackupTrueRegex = 'android:allowBackup\s*=\s*"' + 'true"'
$allowBackupFalseRegex = 'android:allowBackup\s*=\s*"false"'
$networkConfigCleartextRegex = 'cleartextTrafficPermitted\s*=\s*"' + 'true"'
$unsafeTlsRegex = '(AllowAllHostnameVerifier|SSLSocketFactory\.ALLOW_ALL|TrustAll|HostnameVerifier\s*\{[^}]*->\s*true\s*\}|setHostnameVerifier\s*\([^)]*true|checkServerTrusted\s*\([^)]*\)\s*\{[\s/*]*\})'
$unsafeExecRegex = '(Runtime\.getRuntime\(\)\.exec|ProcessBuilder\s*\(|addJavascriptInterface\s*\(|evaluateJavascript\s*\(|loadUrl\s*\(\s*"javascript:|setJavaScriptEnabled\s*\(\s*true\s*\)|DexClassLoader\s*\(|PathClassLoader\s*\(|InMemoryDexClassLoader\s*\()'
$openSocketRegex = '(new\s+ServerSocket\s*\(|ServerSocket\s*\(|ServerSocketChannel\.open|DatagramSocket\s*\(|AsynchronousServerSocketChannel\.open|LocalServerSocket\s*\()'
$cleartextHttpUrlRegex = 'http://(?!(schemas\.android\.com/|www\.w3\.org/|ns\.adobe\.com/|schemas\.xmlsoap\.org/|apache\.org/|gradle\.org/|maven\.apache\.org/))[^\s"''`>]+'
$exportedServiceRegex = '<(service|receiver|provider)\b[^>]*android:exported\s*=\s*"true"'
$orientationLockRegex = 'android:screenOrientation\s*='
$privacyTrackingRegex = '(Settings\.Secure\.ANDROID_ID|AdvertisingIdClient|getImei\s*\(|getDeviceId\s*\(|getMacAddress\s*\(|Build\.SERIAL|Build\.getSerial\s*\()'
$analyticsSdkRegex = '(firebase-analytics|play-services-ads|play-services-measurement|com\.google\.android\.gms|play-services-|com\.google\.firebase|com\.mixpanel|com\.amplitude|com\.appsflyer|io\.branch\.sdk|facebook-android-sdk)'
$usernameRegex = if ($env:USERNAME -and $env:USERNAME.Length -ge 3) { "(?<![A-Za-z0-9_])$([regex]::Escape($env:USERNAME))(?![A-Za-z0-9_])" } else { "" }

$violations = New-Object System.Collections.Generic.List[string]
$scannedCount = 0

foreach ($relPath in $candidateFiles) {
    if ([string]::IsNullOrWhiteSpace($relPath)) { continue }
    $fullPath = Join-Path $RepoRoot $relPath
    if (-not (Test-Path $fullPath -PathType Leaf)) { continue }
    $scannedCount++

    $normalizedRel = $relPath -replace '\\', '/'
    $fileName = [System.IO.Path]::GetFileName($fullPath)
    $ext = [System.IO.Path]::GetExtension($fullPath).ToLowerInvariant()

    if ($normalizedRel -match '(^|/)\.private/' -or
        $forbiddenExtensions -contains $ext -or
        $forbiddenNames -contains $fileName -or
        $fileName -like ".env.*" -or
        $fileName -like "id_rsa*" -or
        $fileName -like "id_ed25519*" -or
        $fileName -like "*service-account*.json" -or
        $fileName -like "*firebase-adminsdk*.json") {
        $violations.Add("${normalizedRel}: Forbidden private/credential/keystore/env file detected.")
        continue
    }

    if ($binaryExtensions -contains $ext) { continue }

    $lines = Get-Content -Path $fullPath -ErrorAction SilentlyContinue
    if ($null -eq $lines) { continue }
    $fullText = [string]::Join("`n", $lines)

    $isAndroidProjectFile = $normalizedRel -like "days/*/android_project/*"
    $isAndroidManifest = $isAndroidProjectFile -and ($fileName -eq "AndroidManifest.xml")
    $isNetworkSecurityConfig = $isAndroidProjectFile -and ($fileName -like "*network_security*.xml")
    $isAndroidCode = $isAndroidProjectFile -and ($ext -in @(".kt", ".java", ".xml", ".gradle", ".kts"))

    $lineNum = 0
    foreach ($line in $lines) {
        $lineNum++
        if ($line -match $winUserPathRegex) {
            $violations.Add("${normalizedRel}:${lineNum}: Windows user directory path exposed.")
        }
        if ($line -match $macUserPathRegex) {
            $violations.Add("${normalizedRel}:${lineNum}: macOS user directory path exposed.")
        }
        if ($line -match $linuxUserPathRegex) {
            $violations.Add("${normalizedRel}:${lineNum}: Linux home directory path exposed.")
        }
        if ($line -match $studioUrlRegex) {
            $violations.Add("${normalizedRel}:${lineNum}: Private Google AI Studio URL exposed.")
        }
        if ($line -match $secretRegex) {
            $violations.Add("${normalizedRel}:${lineNum}: Potential API token or private key detected.")
        }
        if ((-not $isAndroidCode) -and $usernameRegex -ne "" -and ([regex]::IsMatch($line, $usernameRegex, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase))) {
            $violations.Add("${normalizedRel}:${lineNum}: Local OS username detected.")
        }
        if (($ext -in @(".xml", ".gradle", ".kts")) -and ($line -match $cleartextRegex)) {
            $violations.Add("${normalizedRel}:${lineNum}: Insecure AndroidManifest usesCleartextTraffic=true detected.")
        }
        if ($isAndroidManifest -and ($line -match $allowBackupTrueRegex)) {
            $violations.Add("${normalizedRel}:${lineNum}: AndroidManifest android:allowBackup=true detected (must be false).")
        }
        if ($isNetworkSecurityConfig -and ($line -match $networkConfigCleartextRegex)) {
            $violations.Add("${normalizedRel}:${lineNum}: Network security config enables cleartextTrafficPermitted=true.")
        }
        if ($isAndroidCode -and ($line -notmatch 'xmlns:') -and ($line -match $cleartextHttpUrlRegex)) {
            $violations.Add("${normalizedRel}:${lineNum}: Unencrypted http:// URL detected in Android project (HTTPS required).")
        }
    }

    # Multi-line Android code & manifest hardening checks
    if ($isAndroidCode) {
        if ($fullText -match $unsafeTlsRegex) {
            $violations.Add("${normalizedRel}: Permissive TLS/HostnameVerifier bypass detected.")
        }
        if ($fullText -match $unsafeExecRegex) {
            $violations.Add("${normalizedRel}: Dangerous command execution / WebView JS bridge / dynamic code load sink detected.")
        }
        if ($fullText -match $openSocketRegex) {
            $violations.Add("${normalizedRel}: Unauthenticated inbound listening socket detected.")
        }
        if ($fullText -match $privacyTrackingRegex) {
            $violations.Add("${normalizedRel}: Forbidden device-fingerprinting/tracking identifier access detected.")
        }
        if (($ext -in @(".gradle", ".kts")) -and ($fullText -match $analyticsSdkRegex)) {
            $violations.Add("${normalizedRel}: Forbidden third-party analytics/ad SDK dependency detected.")
        }
    }

    if ($isAndroidManifest) {
        if ($fullText -notmatch $cleartextFalseRegex) {
            $violations.Add("${normalizedRel}: AndroidManifest.xml must explicitly set android:usesCleartextTraffic=`"false`".")
        }
        if ($fullText -notmatch $allowBackupFalseRegex) {
            $violations.Add("${normalizedRel}: AndroidManifest.xml must explicitly set android:allowBackup=`"false`".")
        }
        if ($fullText -match $exportedServiceRegex) {
            $violations.Add("${normalizedRel}: Non-launcher service/receiver/provider sets android:exported=`"true`".")
        }
        if ($fullText -match $orientationLockRegex) {
            $violations.Add("${normalizedRel}: Avoid hardcoded android:screenOrientation locks (Android 16 targetSdk 36 requires adaptive screen support).")
        }
    }
}

$passed = ($violations.Count -eq 0)

if ($JsonOutput) {
    $resultObj = [PSCustomObject]@{
        passed       = $passed
        scannedFiles = $scannedCount
        violations   = @($violations)
    }
    $resultObj | ConvertTo-Json -Compress
    if (-not $passed) { exit 1 }
    exit 0
}

if (-not $passed) {
    if (-not $Quiet) {
        Write-Error ("Security audit FAILED with $($violations.Count) violation(s):`n - " + ($violations -join "`n - "))
    } else {
        Write-Output ($violations -join "; ")
    }
    exit 1
}

if (-not $Quiet) {
    Write-Host "[OK] Security & OpSec audit passed across $scannedCount file(s)."
}
exit 0
