[CmdletBinding()]
param(
    [string]$BaseUrl,
    [string]$AdminUsername,
    [string]$AdminPassword,
    [string]$TemplateCode = "DEMO22_V5",
    # The demo bank holds 3 questions per task type, so per-type counts above 3 are capped.
    # Pass -MaxPerType 12 once the bank is large enough for the exact V5 counts.
    [int]$MaxPerType = 3,
    [string]$EnvFile
)

# Creates and ACTIVATES the standard 22-item template from the PTE Score Table V5
# (Official Release), scripts/demo22/data/score-table-v5.json. Only one template can
# be ACTIVE at a time: activating this one retires whichever template is active now
# (sessions already generated keep their pinned copy).
#
# Credentials: PTE_ADMIN_USERNAME / PTE_ADMIN_PASSWORD from the environment or .env.local.
# Idempotent: an already ACTIVE template with the same shape is left alone.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\seed-common.ps1")
if ([string]::IsNullOrWhiteSpace($EnvFile)) { $EnvFile = Join-Path (Split-Path -Parent (Split-Path -Parent $scriptDir)) ".env.local" }
Import-LocalEnv -Path $EnvFile
Set-SeedBaseUrl -BaseUrl $BaseUrl
if ([string]::IsNullOrWhiteSpace($AdminUsername)) {
    $AdminUsername = if ([string]::IsNullOrWhiteSpace($env:PTE_ADMIN_USERNAME)) { "admin@test.local" } else { $env:PTE_ADMIN_USERNAME }
}
$AdminPassword = Resolve-Secret -Value $(if ($AdminPassword) { $AdminPassword } else { $env:PTE_ADMIN_PASSWORD }) -Prompt "Platform admin password"

$table = (Get-Content -Raw -Path (Join-Path $scriptDir "data\score-table-v5.json") | ConvertFrom-Json).items
$desired = @($table | ForEach-Object {
        @{
            taskType        = $_.taskType
            taskTypeKey     = $_.taskType
            section         = $_.section
            sequence        = [int]$_.sequence
            minCount        = [Math]::Min([int]$_.min, $MaxPerType)
            maxCount        = [Math]::Min([int]$_.max, $MaxPerType)
            prepSeconds     = [int]$_.prepSeconds
            responseSeconds = [int]$_.answerSeconds
            speakingWeight  = [decimal]$_.speaking
            writingWeight   = [decimal]$_.writing
            readingWeight   = [decimal]$_.reading
            listeningWeight = [decimal]$_.listening
        }
    })

function Test-TemplateShape {
    param([pscustomobject]$Template)
    $items = Get-Array $Template.items
    if ($items.Count -ne $desired.Count) { return $false }
    foreach ($want in $desired) {
        $have = $items | Where-Object { ($_.taskTypeKey, $_.taskType | Where-Object { $_ } | Select-Object -First 1) -eq $want.taskType } | Select-Object -First 1
        if ($null -eq $have) { return $false }
        if ([int]$have.minCount -ne $want.minCount -or [int]$have.maxCount -ne $want.maxCount) { return $false }
        if ([int]$have.responseSeconds -ne $want.responseSeconds -or [int]$have.prepSeconds -ne $want.prepSeconds) { return $false }
        if ([decimal]$have.speakingWeight -ne $want.speakingWeight -or [decimal]$have.writingWeight -ne $want.writingWeight `
                -or [decimal]$have.readingWeight -ne $want.readingWeight -or [decimal]$have.listeningWeight -ne $want.listeningWeight) { return $false }
    }
    return $true
}

$token = Get-SeedToken -Username $AdminUsername -Password $AdminPassword
$templates = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/score-templates" -Token $token)
$latest = $templates | Where-Object { $_.code -eq $TemplateCode } | Sort-Object -Property version -Descending | Select-Object -First 1

$template = $null
if ($null -ne $latest) {
    $template = Invoke-SeedApi -Method GET -Path "/api/v1/score-templates/$($latest.publicId)" -Token $token
    if ("$($template.status)".ToUpperInvariant() -eq "ACTIVE" -and (Test-TemplateShape $template)) {
        Write-Host "Template $TemplateCode v$($template.version) is already ACTIVE with the expected 22 items."
        Write-Host "Template id: $($template.publicId)"
        return
    }
}

$status = if ($null -eq $template) { "NONE" } else { "$($template.status)".ToUpperInvariant() }
switch ($status) {
    "NONE" {
        $template = Invoke-SeedApi -Method POST -Path "/api/v1/score-templates" -Token $token -Body @{
            code = $TemplateCode; name = "PTE Score Table V5 (Official Release) - demo"; templatePolicy = "STANDARD_PTE"
        }
    }
    "DRAFT" { }
    default {
        # ACTIVE with a different shape, RETIRED, or pending approval: continue from a fresh draft version.
        if ($status -eq "PENDING_APPROVAL") { $template = Invoke-SeedApi -Method POST -Path "/api/v1/score-templates/$($template.publicId)/approve" -Token $token }
        else { $template = Invoke-SeedApi -Method POST -Path "/api/v1/score-templates/$($template.publicId)/clone" -Token $token }
    }
}

Write-Host "== Saving $($desired.Count) items (max $MaxPerType questions per task type) =="
$template = Invoke-SeedApi -Method PUT -Path "/api/v1/score-templates/$($template.publicId)/items" -Token $token -Body @{
    name           = "PTE Score Table V5 (Official Release) - demo"
    templatePolicy = "STANDARD_PTE"
    items          = $desired
}
Write-Host "== Activating (retires the currently active template, if any) =="
$template = Invoke-SeedApi -Method POST -Path "/api/v1/score-templates/$($template.publicId)/activate" -Token $token
Write-Host "Template $($template.code) v$($template.version) is $($template.status)."
Write-Host "Template id: $($template.publicId)"
