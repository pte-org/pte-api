[CmdletBinding()]
param(
    [string]$BaseUrl,
    [string]$AdminUsername,
    [string]$AdminPassword,
    [string]$SeedPassword,
    [string]$TenantCode = "demo22",
    [string]$EnvFile,
    [int]$OpenDays = 14
)

# Creates the demo tenant (host, student, proctor, class, one exam package per exam) and 6 OPEN exams from the
# active DEMO22_V5 template:
#   5 x PRACTICE      "DEMO22 PRACTICE - Full" (all skills) + one per skill (Speaking/Writing/Reading/Listening)
#   1 x OFFICIAL_EXAM "DEMO22 OFFICIAL" (STRICT lockdown, proctor assigned)
#
# Run after seed-demo22-questions.ps1 and seed-demo22-template.ps1. Credentials come from
# PTE_ADMIN_PASSWORD / PTE_SEED_PASSWORD (environment or .env.local); nothing is written to disk.
# Idempotent: an existing DRAFT/READY/SCHEDULED/OPEN exam of the same name is continued, not duplicated.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\seed-common.ps1")
. (Join-Path $scriptDir "lib\seed-tenant.ps1")
if ([string]::IsNullOrWhiteSpace($EnvFile)) { $EnvFile = Join-Path (Split-Path -Parent (Split-Path -Parent $scriptDir)) ".env.local" }
Import-LocalEnv -Path $EnvFile
Set-SeedBaseUrl -BaseUrl $BaseUrl
if ([string]::IsNullOrWhiteSpace($AdminUsername)) {
    $AdminUsername = if ([string]::IsNullOrWhiteSpace($env:PTE_ADMIN_USERNAME)) { "admin@test.local" } else { $env:PTE_ADMIN_USERNAME }
}
$AdminPassword = Resolve-Secret -Value $(if ($AdminPassword) { $AdminPassword } else { $env:PTE_ADMIN_PASSWORD }) -Prompt "Platform admin password"
$SeedPassword = Resolve-Secret -Value $(if ($SeedPassword) { $SeedPassword } else { $env:PTE_SEED_PASSWORD }) -Prompt "Password to assign to the demo host, student and proctor"

$slug = ($TenantCode.ToLowerInvariant() -replace "[^a-z0-9]+", "-").Trim("-")
$hostEmail = "host.$slug@test.local"
$studentEmail = "student.$slug@test.local"
$proctorEmail = "proctor.$slug@test.local"

$adminToken = Get-SeedToken -Username $AdminUsername -Password $AdminPassword

Write-Host "== Checking the active template =="
$template = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/score-templates" -Token $adminToken) |
    Where-Object { "$($_.status)".ToUpperInvariant() -eq "ACTIVE" } | Select-Object -First 1
if ($null -eq $template -or $template.code -ne "DEMO22_V5") {
    $found = if ($null -eq $template) { "none" } else { $template.code }
    throw "Active template is '$found', expected DEMO22_V5. Run scripts\demo22\seed-demo22-template.ps1 first."
}

Write-Host "== Tenant, accounts, class, exam package =="
$tenant = Ensure-Tenant -AdminToken $adminToken -Code $TenantCode -Name "PTE demo $TenantCode" `
    -TaxCode (("DEMO-" + ($slug -replace "-", "")).ToUpperInvariant())
$organization = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/tenants/$($tenant.publicId)/organizations" -Token $adminToken) | Select-Object -First 1
if ($null -eq $organization) { throw "Tenant '$($tenant.code)' has no provisioned organization." }

$null = Ensure-TenantUser -Token $adminToken -ListPath "/api/v1/users/by-tenant/$($tenant.publicId)" -Email $hostEmail `
    -FullName "Demo22 Host" -Password $SeedPassword -Role "HOST_ADMIN" -TenantId $tenant.publicId
$hostToken = Get-SeedToken -Username $hostEmail -Password $SeedPassword
$student = Ensure-TenantUser -Token $hostToken -ListPath "/api/v1/users" -Email $studentEmail `
    -FullName "Demo22 Student" -Password $SeedPassword -Role "STUDENT" -StudentCode "DEMO22-STUDENT"
$proctor = Ensure-TenantUser -Token $hostToken -ListPath "/api/v1/users" -Email $proctorEmail `
    -FullName "Demo22 Proctor" -Password $SeedPassword -Role "PROCTOR"

$program = Ensure-Program -HostToken $hostToken -OrganizationId $organization.publicId -Name "DEMO22 program"
$class = Ensure-Class -HostToken $hostToken -OrganizationId $organization.publicId -ProgramId $program.publicId -Name "DEMO22 class"
$null = Ensure-ClassMembership -HostToken $hostToken -OrganizationId $organization.publicId -ProgramId $program.publicId `
    -ClassId $class.publicId -StudentId $student.publicId

$specs = @(
    @{ Name = "DEMO22 PRACTICE - Full"; Mode = "PRACTICE"; Skills = @("SPEAKING", "WRITING", "READING", "LISTENING"); Retries = 9 },
    @{ Name = "DEMO22 PRACTICE - Speaking"; Mode = "PRACTICE"; Skills = @("SPEAKING"); Retries = 9 },
    @{ Name = "DEMO22 PRACTICE - Writing"; Mode = "PRACTICE"; Skills = @("WRITING"); Retries = 9 },
    @{ Name = "DEMO22 PRACTICE - Reading"; Mode = "PRACTICE"; Skills = @("READING"); Retries = 9 },
    @{ Name = "DEMO22 PRACTICE - Listening"; Mode = "PRACTICE"; Skills = @("LISTENING"); Retries = 9 },
    @{ Name = "DEMO22 OFFICIAL"; Mode = "OFFICIAL_EXAM"; Skills = @("SPEAKING", "WRITING", "READING", "LISTENING"); Retries = 0 }
)

function Format-Utc { param([DateTimeOffset]$Value) return $Value.ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ") }

$sessions = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/sessions" -Token $hostToken)
$results = @()
foreach ($spec in $specs) {
    Write-Host "== $($spec.Name) ($($spec.Mode)) =="
    $session = $sessions | Where-Object { $_.name -eq $spec.Name -and @("DRAFT", "READY", "SCHEDULED", "OPEN") -contains "$($_.status)".ToUpperInvariant() } | Select-Object -First 1
    if ($null -eq $session) {
        # One exam package per exam: sessions on the same subscription may not overlap in time,
        # and all six demo exams are open at once.
        $plan = Ensure-ActivePlan -AdminToken $adminToken -Name "$($spec.Name) package"
        $subscription = Ensure-Subscription -AdminToken $adminToken -HostToken $hostToken -PlanId $plan.publicId
        $now = [DateTimeOffset]::UtcNow
        $closes = $now.AddDays($OpenDays)
        $subscriptionEnd = [DateTimeOffset]$subscription.expiresAt
        if ($closes -gt $subscriptionEnd.AddHours(-1)) { $closes = $subscriptionEnd.AddHours(-1) }
        $session = Invoke-SeedApi -Method POST -Path "/api/v1/sessions/drafts" -Token $hostToken -Body @{
            name                 = $spec.Name
            templatePublicId     = $template.publicId
            subscriptionPublicId = $subscription.publicId
            opensAt              = Format-Utc $now.AddMinutes(2)
            closesAt             = Format-Utc $closes
            examMode             = $spec.Mode
            formMode             = "SHARED_FORM"
            reusePolicy          = "ALLOW"
            capacity             = 30
            selectedSkills       = $spec.Skills
            maxRetriesPerStudent = $spec.Retries
        }
    }

    $status = "$($session.status)".ToUpperInvariant()
    if ($status -eq "DRAFT") {
        $session = Invoke-SeedApi -Method PATCH -Path "/api/v1/sessions/$($session.publicId)" -Token $hostToken -Body @{
            opensAt = Format-Utc ([DateTimeOffset]::UtcNow.AddSeconds(10))
        }
        $sources = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/sessions/$($session.publicId)/audience-sources" -Token $hostToken)
        if (-not ($sources | Where-Object { "$($_.sourceType)" -eq "CLASS" -and $_.sourcePublicId -eq $class.publicId })) {
            $null = Invoke-SeedApi -Method POST -Path "/api/v1/sessions/$($session.publicId)/audience-sources" -Token $hostToken -Body @{
                sourceType = "CLASS"; sourcePublicId = $class.publicId
            }
        }
        if ($spec.Mode -eq "OFFICIAL_EXAM") {
            $assigned = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/sessions/$($session.publicId)/proctors" -Token $hostToken)
            if (-not ($assigned | Where-Object { $_.proctorPublicId -eq $proctor.publicId })) {
                $null = Invoke-SeedApi -Method POST -Path "/api/v1/sessions/$($session.publicId)/proctors" -Token $hostToken -Body @{ proctorPublicId = $proctor.publicId }
            }
        }
        $preflight = Invoke-SeedApi -Method POST -Path "/api/v1/sessions/$($session.publicId)/preflight" -Token $hostToken
        if (-not [bool]$preflight.ready) { throw "Preflight for '$($spec.Name)' is not ready: $((Get-Array $preflight.issues) -join '; ')" }
        $key = "DEMO22-" + ($spec.Name -replace "[^A-Za-z0-9]+", "-").ToUpperInvariant() + "-V1"
        $null = Invoke-SeedApi -Method POST -Path "/api/v1/sessions/$($session.publicId)/generate?idempotencyKey=$([Uri]::EscapeDataString($key))" -Token $hostToken
        $status = "READY"
    }
    if ($status -eq "READY") {
        $null = Invoke-SeedApi -Method POST -Path "/api/v1/sessions/$($session.publicId)/publish" -Token $hostToken
        $status = "SCHEDULED"
    }
    if ($status -eq "SCHEDULED") {
        $session = Invoke-SeedApi -Method POST -Path "/api/v1/sessions/$($session.publicId)/open" -Token $hostToken
    }
    else {
        $session = Invoke-SeedApi -Method GET -Path "/api/v1/sessions/$($session.publicId)" -Token $hostToken
    }
    if ("$($session.status)".ToUpperInvariant() -ne "OPEN") {
        throw "Exam '$($spec.Name)' is $($session.status), expected OPEN (a previous generation may still be running or have failed)."
    }
    $results += [pscustomobject]@{ Name = $spec.Name; Mode = $spec.Mode; Status = $session.status; Id = $session.publicId }
}

Write-Host ""
Write-Host "== DEMO22 sessions ready =="
$results | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
Write-Host "Host login:    $hostEmail"
Write-Host "Student login: $studentEmail"
Write-Host "Proctor login: $proctorEmail"
Write-Host "Password for all three: PTE_SEED_PASSWORD (not printed)."
