[CmdletBinding()]
param(
    [string]$EnvFile,
    [switch]$SkipVerify
)

# One command for the whole DEMO22 demo data (22 task types, PRACTICE + OFFICIAL flows):
#   1. bootstrap-local-admin.ps1     platform admin (skipped if it exists)
#   2. seed-demo22-questions.ps1     question bank, 3 per task type + Personal Introduction
#   3. seed-demo22-template.ps1     PTE Score Table V5 template (activates it)
#   4. seed-demo22-sessions.ps1      demo tenant/accounts + 5 PRACTICE exams + 1 OFFICIAL exam
#   5. verify-demo22.ps1             student preflight on all six
#
# Prerequisite: the compose stack is up (see README) and PTE_ADMIN_PASSWORD / PTE_SEED_PASSWORD
# are set in the environment or .env.local. Safe to run repeatedly.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$common = @{}
if (-not [string]::IsNullOrWhiteSpace($EnvFile)) { $common["EnvFile"] = $EnvFile }

$steps = @(
    @{ Name = "bootstrap-local-admin.ps1"; Args = $common },
    @{ Name = "seed-demo22-questions.ps1"; Args = @{} },
    @{ Name = "seed-demo22-template.ps1"; Args = $common },
    @{ Name = "seed-demo22-sessions.ps1"; Args = $common }
)
if (-not $SkipVerify) { $steps += @{ Name = "verify-demo22.ps1"; Args = $common } }

foreach ($step in $steps) {
    Write-Host ""
    Write-Host "######## $($step.Name) ########"
    $arguments = $step.Args
    & (Join-Path $scriptDir $step.Name) @arguments
}
Write-Host ""
Write-Host "DEMO22 seed complete."
