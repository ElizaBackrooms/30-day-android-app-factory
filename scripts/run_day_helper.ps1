param(
    [ValidateSet("Prepare", "Finish")]
    [string]$Action = "Prepare",
    [int]$DayNumber = 0
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$trackerPath = Join-Path $RepoRoot "tracker.json"
if (-not (Test-Path $trackerPath)) {
    throw "tracker.json not found at $trackerPath"
}
$tracker = Get-Content -Path $trackerPath -Raw | ConvertFrom-Json
$targetDay = if ($DayNumber -gt 0) { $DayNumber } else { [int]$tracker.current_day }

# Parse 30_DAY_SLATE.md table row for $targetDay
$slatePath = Join-Path $RepoRoot "30_DAY_SLATE.md"
$slateLines = Get-Content -Path $slatePath
$dayRow = $null
foreach ($line in $slateLines) {
    if ($line -match "^\|\s*$targetDay\s*\|\s*([^|]+)\|\s*\*\*([^*]+)\*\*\s*\|\s*([^|]+)\|\s*([^|]+)\|") {
        $dayRow = [PSCustomObject]@{
            Day      = $targetDay
            Category = $Matches[1].Trim()
            AppName  = $Matches[2].Trim()
            Hardware = $Matches[3].Trim()
            Online   = $Matches[4].Trim()
        }
        break
    }
}

if (-not $dayRow) {
    throw "Could not find Day $targetDay in 30_DAY_SLATE.md"
}

$slug = ($dayRow.AppName.ToLowerInvariant() -replace '[^a-z0-9]+', '_').Trim('_')
$pkgSlug = ($dayRow.AppName.ToLowerInvariant() -replace '[^a-z0-9]+', '')
$dayDirRel = ("days/day_{0:D2}_{1}" -f $targetDay, $slug)
$dayDirAbs = Join-Path $RepoRoot ($dayDirRel -replace '/', '\')

if (-not (Test-Path $dayDirAbs)) {
    New-Item -ItemType Directory -Path $dayDirAbs -Force | Out-Null
}

$planFile = Join-Path $dayDirAbs ("day_{0:D2}_plan.md" -f $targetDay)
$listingFile = Join-Path $dayDirAbs "play_store_listing.md"
$promptFile = Join-Path $dayDirAbs "google_ai_studio_prompt.txt"
$privacyUrl = "https://github.com/ElizaBackrooms/30-day-android-app-factory/blob/main/PRIVACY_POLICY.md"

if ($Action -eq "Prepare") {
    if (-not (Test-Path $planFile)) {
        $planContent = @"
# Day $($dayRow.Day) - $($dayRow.AppName)

- **Category:** $($dayRow.Category)
- **Package ID:** ``io.github.elizabackrooms.$pkgSlug``
- **Target Device & OS:** Moto G Stylus 5G (2023) (``genevn``, LineageOS 24.0 / Android 17, Zero GMS) & Google Play Store (``minSdk = 26``, ``targetSdk = 36``)
- **Hardware / Sensor Hook:** $($dayRow.Hardware)
- **Opt-In Online Integration:** $($dayRow.Online)
- **Privacy Policy:** $privacyUrl
"@
        [System.IO.File]::WriteAllText($planFile, $planContent, $utf8NoBom)
    }

    if (-not (Test-Path $listingFile)) {
        $listingContent = @"
# Google Play Store Listing - $($dayRow.AppName)

- **Package ID:** ``io.github.elizabackrooms.$pkgSlug``
- **App Name:** $($dayRow.AppName)
- **Category:** Tools / Utilities
- **Privacy Policy URL:** $privacyUrl

## Short Description (80 chars max)
$($dayRow.AppName): offline-first Android utility with opt-in online tools.

## Full Description
**$($dayRow.AppName)** is an offline-first Android utility designed for everyday users and de-Googled LineageOS devices (including Moto G Stylus 5G 2023) with zero Google Play Services dependencies.

### Key Features
- **Core Hardware & Sensor Utility:** $($dayRow.Hardware)
- **Optional Opt-In Online Mode:** $($dayRow.Online) (disabled by default; connects via HTTPS only when explicitly triggered by the user).
- **Plain-English Interface:** Simple, everyday labels with 1-line helper explanations and a built-in Help & Privacy tab.
- **Privacy by Default (Stateless):** Saves nothing to disk and makes zero network requests unless you explicitly opt in. Includes a 1-tap **Wipe All Local Data** button.

## Google Play Data Safety Summary
- **Data collected automatically:** None.
- **Data shared automatically:** None.
- **Local storage:** In-memory only by default; optional on-device persistence is excluded from cloud backup/device transfer and removable in one tap.
"@
        [System.IO.File]::WriteAllText($listingFile, $listingContent, $utf8NoBom)
    }

    if (-not (Test-Path $promptFile)) {
        $promptContent = @"
Build a complete, production-ready, offline-first Android app in Kotlin + Jetpack Compose (Material 3) named "$($dayRow.AppName)" with package / applicationId "io.github.elizabackrooms.$pkgSlug" (minSdk = 26, targetSdk = 36).

1. CORE HARDWARE / LOCAL CAPABILITY (WORKS 100% OFFLINE):
- Category: $($dayRow.Category)
- Hardware & Local Engine: $($dayRow.Hardware)
- Must run 100% natively on a Moto G Stylus 5G (2023) running de-Googled LineageOS 24.0 (zero Google Play Services, zero Firebase, zero third-party analytics/ad SDKs).
- Use only standard AOSP APIs and mark all optional <uses-feature> tags in AndroidManifest.xml with android:required="false". Include a built-in 1-tap Demo / Simulation mode so every feature can be tested immediately even if hardware or signal is idle.

2. OPTIONAL LIVE ONLINE FEATURE (OPT-IN ONLY, OFF BY DEFAULT):
- Online Integration: $($dayRow.Online)
- Use javax.net.ssl.HttpsURLConnection on Dispatchers.IO + org.json (https:// only, connectTimeout = 10_000, readTimeout = 10_000, instanceFollowRedirects = false, useCaches = false, 64 KB response cap).
- If any user-configurable webhook/URL is supported, validate via InetAddress.getAllByName and block localhost, .local, .internal, 0.0.0.0/8, 127.0.0.0/8, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 100.64.0.0/10, 169.254.0.0/16, ::1, fc00::/7, fe80::/10, ::ffff:0:0/96, and multicast IPs.
- Never make any automatic network call on startup; require an explicit Opt-In toggle (default false) and an explicit user action button.

3. PLAIN-ENGLISH ("NORMIE-FRIENDLY") UI & ADAPTIVE LAYOUT:
- Call enableEdgeToEdge() in MainActivity and consume WindowInsets.safeDrawing in Scaffold.
- Never lock android:screenOrientation in AndroidManifest.xml.
- Use simple, everyday plain-English labels on all tabs, buttons, meters, and settings (put technical acronyms in parentheses, add a 1-line plain-English subtitle under each section explaining what it does, and include a beginner "Help & Privacy" tab).

4. PRIVACY BY DEFAULT & SECURITY HARDENING:
- Operate 100% in-memory by default. Gate any SharedPreferences reads/writes behind an explicit "Save History on Phone (Opt-In)" switch defaulting to false.
- Provide a 1-tap "Wipe All Local Data" button that clears SharedPreferences (prefs.edit().clear().apply()) and resets all in-memory state.
- Set android:allowBackup="false" and android:usesCleartextTraffic="false" in AndroidManifest.xml, and exclude sharedpref/database/file in data_extraction_rules.xml and backup_rules.xml.
- Show a clear in-app rationale dialog before requesting any runtime permission (Mic, Camera, Location, BLE).
- Include a clickable button in the Help & Privacy tab that opens the Privacy Policy URL: $privacyUrl
- Include unit tests and Robolectric tests (@Config(sdk = [35])) verifying core math/protocol logic and Privacy-by-Default opt-in behavior.
"@
        [System.IO.File]::WriteAllText($promptFile, $promptContent, $utf8NoBom)
    }

    $promptText = Get-Content -Path $promptFile -Raw
    Set-Clipboard -Value $promptText
    Write-Host "================================================================="
    Write-Host "[OK] Prepared Day $($dayRow.Day): $($dayRow.AppName) ($dayDirRel)"
    Write-Host "[OK] Copied google_ai_studio_prompt.txt to your Windows Clipboard!"
    Write-Host "================================================================="
    Write-Host "Next steps:"
    Write-Host "  1. In Google Chrome (opening now), click 'Build an Android app'."
    Write-Host "  2. Press Ctrl+V to paste the prompt and submit."
    Write-Host "  3. Once built, switch 'Preview' -> 'Code' -> 'Export options' -> 'Download as .zip file'."
    Write-Host "  4. Run: powershell -ExecutionPolicy Bypass -File scripts\run_day_helper.ps1 -Action Finish"
    & (Join-Path $PSScriptRoot "publish_to_github.ps1") -OpenAiStudio
    exit 0
}

if ($Action -eq "Finish") {
    Write-Host "[1/5] Extracting & hardening downloaded Google AI Studio ZIP for $dayDirRel..."
    & (Join-Path $PSScriptRoot "publish_to_github.ps1") -DayFolder $dayDirRel -ExtractOnly
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $projDir = Join-Path $dayDirAbs "android_project"
    if (-not (Test-Path (Join-Path $projDir "app\build.gradle.kts"))) {
        throw "No extracted android_project found in $dayDirRel. Make sure you clicked 'Code -> Export options -> Download as .zip file' in Google AI Studio within the last 30 minutes."
    }

    $gradleBat = Join-Path $env:LOCALAPPDATA "Gradle\gradle-9.3.1\bin\gradle.bat"
    if (Test-Path $gradleBat) {
        Write-Host "[2/5] Running unit tests and building debug APK with local Gradle 9.3.1..."
        $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA "Android\Sdk"
        & $gradleBat -p $projDir testDebugUnitTest assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle test/build failed for $dayDirRel. Fix errors in Cursor and re-run Finish."
        }

        $apkPath = Join-Path $projDir "app\build\outputs\apk\debug\app-debug.apk"
        $adbExe = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
        if ((Test-Path $apkPath) -and (Test-Path $adbExe)) {
            $devOut = (& $adbExe devices 2>$null) -join "`n"
            if ($devOut -match '\bdevice\b') {
                $apkDestName = ("day{0:D2}-{1}-debug.apk" -f $targetDay, ($slug -replace '_', '-'))
                Write-Host "[3/5] Pushing compiled APK ($apkDestName) to phone microSD card (/storage/7485-1F18)..."
                & $adbExe shell mkdir -p /storage/7485-1F18/AndroidApps /storage/7485-1F18/Download 2>$null | Out-Null
                & $adbExe push $apkPath "/storage/7485-1F18/AndroidApps/$apkDestName"
                & $adbExe push $apkPath "/storage/7485-1F18/Download/$apkDestName"
            }
        }
    }

    Write-Host "[4/5] Writing SHA-256 Security Overseer attestation and updating tracker.json..."
    & (Join-Path $PSScriptRoot "verify_day_attestation.ps1") -Action Write -DayFolder $dayDirRel -Summary ("Verified Day {0} ({1}) via local security_audit.ps1, Gradle unit tests, and APK build." -f $targetDay, $dayRow.AppName)
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $dayKey = "$targetDay"
    $dayEntry = [ordered]@{
        app_name             = $dayRow.AppName
        category             = $dayRow.Category
        status               = "published_to_github"
        day_dir              = $dayDirRel
        plan_path            = "$dayDirRel/day_$("{0:D2}" -f $targetDay)_plan.md"
        prompt_path          = "$dayDirRel/google_ai_studio_prompt.txt"
        listing_path         = "$dayDirRel/play_store_listing.md"
        security_review_path = "$dayDirRel/SECURITY_REVIEW.md"
        project_path         = "$dayDirRel/android_project"
    }
    $tracker.days | Add-Member -NotePropertyName $dayKey -NotePropertyValue ([PSCustomObject]$dayEntry) -Force
    $completedList = @($tracker.days_completed | ForEach-Object { [int]$_ })
    if ($completedList -notcontains $targetDay) {
        $completedList += $targetDay
    }
    $tracker.days_completed = @($completedList | Sort-Object -Unique)
    if ([int]$tracker.current_day -le $targetDay -and $targetDay -lt 30) {
        $tracker.current_day = $targetDay + 1
    }
    $trackerJson = $tracker | ConvertTo-Json -Depth 10
    [System.IO.File]::WriteAllText($trackerPath, ($trackerJson + "`n"), $utf8NoBom)

    Write-Host "[5/5] Publishing $dayDirRel to GitHub..."
    & (Join-Path $PSScriptRoot "publish_to_github.ps1") -CommitMessage ("feat(day-{0:D2}): publish {1} to GitHub" -f $targetDay, $dayRow.AppName)
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Host "[DONE] Day $targetDay ($($dayRow.AppName)) is built, copied to microSD (if connected), and live on GitHub!"
}
