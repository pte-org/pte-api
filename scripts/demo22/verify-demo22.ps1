[CmdletBinding()]
param(
    [string]$BaseUrl,
    [string]$TenantCode = "demo22",
    [string]$SeedPassword,
    [string]$EnvFile,
    # Starts (or resumes) an attempt in "DEMO22 PRACTICE - Full" and lists the tasks it serves.
    # Practice allows retries, so this does not use up the student's only chance. It is never
    # done for the OFFICIAL exam, which is one-shot.
    [switch]$StartPracticeAttempt
)

# Read-only check of the DEMO22 exams as the demo student: capability preflight for all six,
# optionally the task list of one practice attempt. Prints names and counts only.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\seed-common.ps1")
if ([string]::IsNullOrWhiteSpace($EnvFile)) { $EnvFile = Join-Path (Split-Path -Parent (Split-Path -Parent $scriptDir)) ".env.local" }
Import-LocalEnv -Path $EnvFile
Set-SeedBaseUrl -BaseUrl $BaseUrl
$SeedPassword = Resolve-Secret -Value $(if ($SeedPassword) { $SeedPassword } else { $env:PTE_SEED_PASSWORD }) -Prompt "Demo user password"

$slug = ($TenantCode.ToLowerInvariant() -replace "[^a-z0-9]+", "-").Trim("-")
$hostToken = Get-SeedToken -Username "host.$slug@test.local" -Password $SeedPassword
$studentToken = Get-SeedToken -Username "student.$slug@test.local" -Password $SeedPassword

# Every standard runtime contract (22 scored types + Personal Introduction) and every
# client capability, i.e. what a fully featured client advertises.
$taskTypes = (Get-Content -Raw -Path (Join-Path $scriptDir "data\score-table-v5.json") | ConvertFrom-Json).items |
    ForEach-Object { $_.taskType }
$taskTypes = @("PERSONAL_INTRODUCTION") + @($taskTypes)
$manifest = @{
    capabilities       = @("AUDIO_PLAYBACK", "AUDIO_RECORDING", "DRAG_AND_DROP", "DROPDOWN_SELECTION", "HIGHLIGHT_SELECTION", "IMAGE_DISPLAY", "OPTION_SELECTION", "TEXT_INPUT")
    appVersion         = "1.0.0"
    supportedContracts = @($taskTypes | ForEach-Object {
            @{ screenKey = "$($_)_V1"; contractVersion = 1; answerSchemaVersion = 1; scoringProfileVersion = 1 }
        })
}

$sessions = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/sessions" -Token $hostToken) |
    Where-Object { $_.name -like "DEMO22 *" -and "$($_.status)".ToUpperInvariant() -eq "OPEN" } | Sort-Object name
if ($sessions.Count -eq 0) { throw "No OPEN DEMO22 sessions found. Run scripts\demo22\seed-demo22-sessions.ps1." }

$rows = foreach ($session in $sessions) {
    $preflight = Invoke-SeedApi -Method POST -Path "/api/v1/attempts/preflight" -Token $studentToken -Body @{
        sessionPublicId = $session.publicId; capabilityManifest = $manifest
    }
    [pscustomobject]@{
        Exam        = $session.name
        Mode        = $session.examMode
        CanStart    = $preflight.canStart
        Missing     = (Get-Array $preflight.missingCapabilities) -join ","
        Unsupported = (Get-Array $preflight.unsupportedTasks | ForEach-Object { $_.taskTypeKey }) -join ","
    }
}
$rows | Format-Table -AutoSize | Out-String -Width 200 | Write-Host

if ($StartPracticeAttempt) {
    $full = $sessions | Where-Object { $_.name -eq "DEMO22 PRACTICE - Full" } | Select-Object -First 1
    $attempt = Invoke-SeedApi -Method POST -Path "/api/v1/attempts" -Token $studentToken -Body @{
        sessionPublicId = $full.publicId; deviceCheckConfirmed = $true; capabilityManifest = $manifest
    }
    $attemptId = if ($attempt.attemptPublicId) { $attempt.attemptPublicId } else { $attempt.publicId }
    $tasks = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/attempts/$attemptId/tasks" -Token $studentToken)
    Write-Host "Attempt $attemptId serves $($tasks.Count) tasks."
    $byType = $tasks | Group-Object { $_.task.taskType } | Sort-Object Name
    foreach ($group in $byType) {
        $first = $group.Group[0].task
        Write-Host ("  {0,-36} x{1}  audio={2} image={3}" -f $group.Name, $group.Count, [bool]$first.audioUrl, [bool]$first.imageUrl)
    }
    $missing = @($taskTypes | Where-Object { $_ -notin $byType.Name })
    if ($missing.Count -gt 0) { throw "Task types missing from the attempt: $($missing -join ', ')" }
    Write-Host "All $($taskTypes.Count) task types (22 scored + Personal Introduction) are served."
}
