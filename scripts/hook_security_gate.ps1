$ErrorActionPreference = "Stop"

function Write-Deny([string]$reason) {
    $deny = @{
        decision = "deny"
        reason   = $reason
    } | ConvertTo-Json -Compress
    Write-Output $deny
    exit 0
}

try {
    $rawInput = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($rawInput)) {
        Write-Output '{"decision":"allow"}'
        exit 0
    }

    $payload = $rawInput | ConvertFrom-Json
    $toolName = ""
    $cmdLine = ""

    if ($null -ne $payload.toolCall) {
        $toolName = [string]$payload.toolCall.name
        if ($null -ne $payload.toolCall.args) {
            foreach ($propName in @("CommandLine", "command_line", "command", "cmd")) {
                if ($null -ne $payload.toolCall.args.$propName) {
                    $cmdLine = [string]$payload.toolCall.args.$propName
                    break
                }
            }
        }
    } elseif ($null -ne $payload.tool_input -and $null -ne $payload.tool_input.command) {
        $toolName = "run_command"
        $cmdLine = [string]$payload.tool_input.command
    }

    if ($toolName -eq "run_command" -and -not [string]::IsNullOrWhiteSpace($cmdLine)) {
        # Block force-adding gitignored files regardless of git flags, quoted -C paths, or target ordering
        $gitPrefixRegex = '(?i)(?<![A-Za-z0-9_.-])git(\.exe)?(?:\s+(?:-C\s+(?:"[^"]+"|\S+)|-c\s+(?:"[^"]+"|\S+)|-[A-Za-z]+|--[A-Za-z-]+(?:=(?:"[^"]+"|\S+))?))*'
        if ($cmdLine -match "${gitPrefixRegex}\s+add\b" -and
            $cmdLine -match '(?i)(\s-[A-Za-z]*f[A-Za-z]*\b|\s--force\b)') {
            Write-Deny "Security Overseer Hook blocked 'git add -f/--force' to protect gitignored secrets and .private files."
        }

        # Block leaking auth tokens, private URLs, keystores, or local env/property files to stdout
        if ($cmdLine -match '(?i)\bgh(\.exe)?\s+auth\s+(token\b|status\b.*(\s-[A-Za-z]*t[A-Za-z]*\b|\s--show-token\b))' -or
            $cmdLine -match '(?i)(local_studio_urls\.json|(^|[\\/\s"''`])\.private([\\/]|$)|keystore\.properties|signing\.properties|local\.properties|\.(jks|keystore|p12|pfx|pem|key)\b)') {
            Write-Deny "Security Overseer Hook blocked command referencing raw auth tokens, keystores, or .private files."
        }

        # Always run security_audit.ps1 AND verify_day_attestation.ps1 before any direct git commit or git push command
        if ($cmdLine -match "${gitPrefixRegex}\s+(commit|push)\b") {
            $auditScript = Join-Path $PSScriptRoot "security_audit.ps1"
            $attestationScript = Join-Path $PSScriptRoot "verify_day_attestation.ps1"
            $prevEAP = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            $auditOut = & powershell -NoProfile -ExecutionPolicy Bypass -File $auditScript -Quiet 2>&1
            $auditCode = $LASTEXITCODE
            $attestOut = & powershell -NoProfile -ExecutionPolicy Bypass -File $attestationScript -Action Verify -Quiet 2>&1
            $attestCode = $LASTEXITCODE
            $ErrorActionPreference = $prevEAP

            if ($auditCode -ne 0) {
                Write-Deny "Security Overseer Hook blocked git commit/push because security_audit.ps1 failed: $($auditOut -join ' ')"
            }
            if ($attestCode -ne 0) {
                Write-Deny "Security Overseer Hook blocked git commit/push because SECURITY_REVIEW.md attestation failed: $($attestOut -join ' ')"
            }
        }
    }

    Write-Output '{"decision":"allow"}'
    exit 0
} catch {
    Write-Deny "Security Overseer Hook encountered a parse/check error: $($_.Exception.Message)"
}
