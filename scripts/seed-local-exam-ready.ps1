[CmdletBinding()]
param(
    [string]$BaseUrl = $(if ([string]::IsNullOrWhiteSpace($env:PTE_BASE_URL)) { "http://localhost:8080" } else { $env:PTE_BASE_URL }),
    [string]$AdminUsername = $(if ([string]::IsNullOrWhiteSpace($env:PTE_ADMIN_USERNAME)) { "admin@test.local" } else { $env:PTE_ADMIN_USERNAME }),
    [string]$AdminPassword = $env:PTE_ADMIN_PASSWORD,
    [string]$SeedPassword = $env:PTE_SEED_PASSWORD,
    [string]$SeedKey = "PTE_LOCAL",
    [string]$HostEmail,
    [string]$StudentEmail,
    [switch]$SkipStartAttempt
)

# Prerequisites:
# - Run seed-local-template-and-question-bank.ps1 first with the same SeedKey.
# - A PLATFORM_ADMIN already exists; this script uses it to onboard the local tenant.
# - The supplied SeedPassword is assigned to both seed accounts (unless an
#   existing account is reused, in which case its password is reset deliberately).
#
# Example:
#   $env:PTE_ADMIN_PASSWORD = '<local-admin-password>'
#   $env:PTE_SEED_PASSWORD = '<local-seed-password>'
#   .\scripts\seed-local-exam-ready.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")

function Resolve-Secret {
    param(
        [string]$Value,
        [Parameter(Mandatory = $true)][string]$Prompt
    )

    if (-not [string]::IsNullOrWhiteSpace($Value)) {
        return $Value
    }

    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function ConvertTo-ApiBody {
    param([AllowNull()][object]$Body)
    if ($null -eq $Body) {
        return $null
    }
    return ($Body | ConvertTo-Json -Depth 20 -Compress)
}

function Invoke-SeedApi {
    param(
        [Parameter(Mandatory = $true)][ValidateSet("GET", "POST", "PUT", "PATCH", "DELETE")][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [string]$Token,
        [AllowNull()][object]$Body
    )

    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers["Authorization"] = "Bearer $Token"
    }

    $request = @{
        Uri         = "$BaseUrl$Path"
        Method      = $Method
        Headers     = $headers
        ErrorAction = "Stop"
    }
    $json = ConvertTo-ApiBody $Body
    if ($null -ne $json) {
        $request["ContentType"] = "application/json"
        $request["Body"] = $json
    }

    try {
        $response = Invoke-RestMethod @request
    }
    catch {
        $detail = $_.Exception.Message
        if ($_.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
            $detail = $_.ErrorDetails.Message
        }
        throw "API $Method $Path failed: $detail"
    }

    if ($null -ne $response -and $response.PSObject.Properties.Name -contains "success" -and -not [bool]$response.success) {
        $code = if ($response.code) { " [$($response.code)]" } else { "" }
        $message = if ($response.userMessage) { $response.userMessage } elseif ($response.message) { $response.message } else { "Unknown API error" }
        throw "API $Method $Path returned an error${code}: $message"
    }

    if ($null -ne $response -and $response.PSObject.Properties.Name -contains "data") {
        return $response.data
    }
    return $response
}

function Get-Array {
    param([AllowNull()][object]$Value)
    if ($null -eq $Value) {
        return @()
    }
    return @($Value)
}

function Get-Token {
    param(
        [string]$Username,
        [string]$Password
    )

    $login = Invoke-SeedApi -Method POST -Path "/api/v1/auth/login" -Body @{
        username = $Username
        password = $Password
    }
    if ([string]::IsNullOrWhiteSpace($login.accessToken)) {
        throw "Login for '$Username' did not return an access token."
    }
    return $login.accessToken
}

function Get-Slug {
    param([string]$Value)
    $slug = ($Value.ToLowerInvariant() -replace "[^a-z0-9]+", "-").Trim("-")
    if ([string]::IsNullOrWhiteSpace($slug)) {
        $slug = "local"
    }
    return $slug.Substring(0, [Math]::Min(20, $slug.Length))
}

function Find-UserByEmail {
    param(
        [AllowNull()][object]$Rows,
        [string]$Email
    )
    return (Get-Array $Rows | Where-Object { $_.email -eq $Email -or $_.username -eq $Email } | Select-Object -First 1)
}

function Ensure-HostUser {
    param(
        [string]$TenantId,
        [string]$Email,
        [string]$Password
    )

    $rows = Invoke-SeedApi -Method GET -Path "/api/v1/users/by-tenant/$TenantId" -Token $script:AdminToken
    $user = Find-UserByEmail -Rows $rows -Email $Email
    if ($null -eq $user) {
        $user = Invoke-SeedApi -Method POST -Path "/api/v1/users" -Token $script:AdminToken -Body @{
            email    = $Email
            fullName = "Local Seed Host"
            password = $Password
            roles    = @("HOST_ADMIN")
            tenantId = $TenantId
        }
    }
    else {
        $null = Invoke-SeedApi -Method POST -Path "/api/v1/users/$($user.publicId)/reset-password" -Token $script:AdminToken -Body @{ newPassword = $Password }
    }
    return $user
}

function Ensure-StudentUser {
    param(
        [string]$Email,
        [string]$Password
    )

    $rows = Invoke-SeedApi -Method GET -Path "/api/v1/users" -Token $script:HostToken
    $user = Find-UserByEmail -Rows $rows -Email $Email
    if ($null -eq $user) {
        $user = Invoke-SeedApi -Method POST -Path "/api/v1/users" -Token $script:HostToken -Body @{
            email       = $Email
            fullName    = "Local Seed Student"
            password    = $Password
            roles       = @("STUDENT")
            studentCode = "SEED-$($SeedKey.ToUpperInvariant())"
        }
    }
    else {
        $null = Invoke-SeedApi -Method POST -Path "/api/v1/users/$($user.publicId)/reset-password" -Token $script:HostToken -Body @{ newPassword = $Password }
    }
    return $user
}

function Ensure-Program {
    param(
        [string]$OrganizationId,
        [string]$Name
    )

    $programs = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/organizations/$OrganizationId/programs" -Token $script:HostToken)
    $program = $programs | Where-Object { $_.name -eq $Name } | Select-Object -First 1
    if ($null -eq $program) {
        $program = Invoke-SeedApi -Method POST -Path "/api/v1/organizations/$OrganizationId/programs" -Token $script:HostToken -Body @{
            name        = $Name
            description = "Local seed data for pte-app exam verification"
            startDate   = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
            endDate     = (Get-Date).ToUniversalTime().AddYears(1).ToString("yyyy-MM-dd")
        }
    }
    return $program
}

function Ensure-Class {
    param(
        [string]$OrganizationId,
        [string]$ProgramId,
        [string]$Name
    )

    $classes = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/organizations/$OrganizationId/programs/$ProgramId/classes" -Token $script:HostToken)
    $class = $classes | Where-Object { $_.name -eq $Name } | Select-Object -First 1
    if ($null -eq $class) {
        $class = Invoke-SeedApi -Method POST -Path "/api/v1/organizations/$OrganizationId/programs/$ProgramId/classes" -Token $script:HostToken -Body @{ name = $Name }
    }
    return $class
}

function Ensure-ClassMembership {
    param(
        [string]$OrganizationId,
        [string]$ProgramId,
        [string]$ClassId,
        [string]$StudentId
    )

    $memberships = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/class-memberships?programPublicId=$ProgramId" -Token $script:HostToken)
    $membership = $memberships | Where-Object { $_.classPublicId -eq $ClassId -and $_.studentPublicId -eq $StudentId } | Select-Object -First 1
    if ($null -eq $membership) {
        $membership = Invoke-SeedApi -Method POST -Path "/api/v1/organizations/$OrganizationId/programs/$ProgramId/classes/$ClassId/students" -Token $script:HostToken -Body @{ studentPublicId = $StudentId }
    }
    return $membership
}

function Ensure-ActivePlan {
    param([string]$Name)

    $plans = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/plans" -Token $script:AdminToken)
    $plan = $plans | Where-Object { $_.name -eq $Name } | Select-Object -First 1
    if ($null -eq $plan -or "$($plan.status)".ToUpperInvariant() -eq "ARCHIVED") {
        $planName = if ($null -eq $plan) { $Name } else { "$Name $(Get-Date -Format yyyyMMddHHmmss)" }
        $plan = Invoke-SeedApi -Method POST -Path "/api/v1/plans" -Token $script:AdminToken -Body @{
            name                   = $planName
            description            = "Local seed exam package"
            type                   = "EXAM_PACKAGE"
            price                  = 0
            currency               = "VND"
            durationDays           = 30
            maxStudentsPerSession = 50
        }
    }
    if ("$($plan.status)".ToUpperInvariant() -eq "DRAFT") {
        $plan = Invoke-SeedApi -Method POST -Path "/api/v1/plans/$($plan.publicId)/activation" -Token $script:AdminToken
    }
    if ("$($plan.status)".ToUpperInvariant() -ne "ACTIVE") {
        throw "Seed plan '$($plan.name)' is not active."
    }
    return $plan
}

function Ensure-Subscription {
    param([string]$PlanId)

    $subscriptions = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/subscriptions" -Token $script:HostToken)
    $now = [DateTimeOffset]::UtcNow
    $subscription = $subscriptions |
        Where-Object {
            $_.planId -eq $PlanId -and
            "$($_.status)".ToUpperInvariant() -eq "ACTIVE" -and
            ([DateTimeOffset]$_.expiresAt) -gt $now
        } |
        Select-Object -First 1
    if ($null -ne $subscription) {
        return $subscription
    }

    $expires = $now.AddDays(30).ToString("yyyy-MM-ddTHH:mm:ssZ")
    $license = Invoke-SeedApi -Method POST -Path "/api/v1/license-codes" -Token $script:AdminToken -Body @{
        planId        = $PlanId
        codeExpiresAt = $expires
    }
    $null = Invoke-SeedApi -Method POST -Path "/api/v1/license-code-redemptions" -Token $script:HostToken -Body @{ code = $license.code }
    $subscriptions = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/subscriptions" -Token $script:HostToken)
    $subscription = $subscriptions |
        Where-Object { $_.planId -eq $PlanId -and "$($_.status)".ToUpperInvariant() -eq "ACTIVE" } |
        Select-Object -First 1
    if ($null -eq $subscription) {
        throw "The license code was redeemed but no active subscription was returned."
    }
    return $subscription
}

function New-CapabilityManifest {
    return @{
        capabilities = @("AUDIO_RECORDING", "OPTION_SELECTION")
        appVersion = "1.0.0"
        supportedContracts = @(
            @{ screenKey = "READ_ALOUD_V1"; contractVersion = 1; answerSchemaVersion = 1; scoringProfileVersion = 1 },
            @{ screenKey = "MC_READING_SINGLE_V1"; contractVersion = 1; answerSchemaVersion = 1; scoringProfileVersion = 1 }
        )
    }
}

$AdminPassword = Resolve-Secret -Value $AdminPassword -Prompt "Platform admin password"
$SeedPassword = Resolve-Secret -Value $SeedPassword -Prompt "Password to assign to the seed host and student"
$script:AdminToken = Get-Token -Username $AdminUsername -Password $AdminPassword
$slug = Get-Slug $SeedKey
$tenantCode = "seed-$slug"
$tenantName = "PTE local seed $SeedKey"
$hostEmail = if ([string]::IsNullOrWhiteSpace($HostEmail)) { "host.$slug@test.local" } else { $HostEmail }
$studentEmail = if ([string]::IsNullOrWhiteSpace($StudentEmail)) { "student.$slug@test.local" } else { $StudentEmail }
$programName = "Local seed program $SeedKey"
$className = "Local seed class $SeedKey"
$planName = "PTE local exam package $SeedKey"
$seedToken = (($SeedKey -replace "[^A-Za-z0-9_]", "_").ToUpperInvariant())
$seedToken = $seedToken.Substring(0, [Math]::Min(52, $seedToken.Length))
$templateCode = $seedToken + "_TEMPLATE"
$examName = "PTE local exam $SeedKey"

Write-Host "== Creating or reusing tenant =="
$tenants = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/tenants" -Token $script:AdminToken)
$tenant = $tenants | Where-Object { $_.code -eq $tenantCode } | Select-Object -First 1
if ($null -eq $tenant) {
    $taxCode = ("PTE-LOCAL-" + ($slug -replace "-", "")).ToUpperInvariant()
    $tenant = Invoke-SeedApi -Method POST -Path "/api/v1/tenants" -Token $script:AdminToken -Body @{
        code             = $tenantCode
        name             = $tenantName
        organizationType = "SCHOOL"
        taxCode          = $taxCode.Substring(0, [Math]::Min(64, $taxCode.Length))
        packageName      = "STANDARD"
        studentLimit     = 50
    }
}
if ("$($tenant.status)".ToUpperInvariant() -ne "ACTIVE") {
    $tenant = Invoke-SeedApi -Method POST -Path "/api/v1/tenants/$($tenant.publicId)/reactivate" -Token $script:AdminToken
}

$organizations = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/tenants/$($tenant.publicId)/organizations" -Token $script:AdminToken)
$organization = $organizations | Select-Object -First 1
if ($null -eq $organization) {
    throw "Tenant '$($tenant.code)' has no provisioned organization."
}
$script:OrganizationId = $organization.publicId

Write-Host "== Creating or reusing host and student accounts =="
$hostUser = Ensure-HostUser -TenantId $tenant.publicId -Email $hostEmail -Password $SeedPassword
$script:HostToken = Get-Token -Username $hostEmail -Password $SeedPassword
$studentUser = Ensure-StudentUser -Email $studentEmail -Password $SeedPassword
$studentToken = Get-Token -Username $studentEmail -Password $SeedPassword

Write-Host "== Creating or reusing program, class, and class membership =="
$program = Ensure-Program -OrganizationId $script:OrganizationId -Name $programName
$class = Ensure-Class -OrganizationId $script:OrganizationId -ProgramId $program.publicId -Name $className
$null = Ensure-ClassMembership -OrganizationId $script:OrganizationId -ProgramId $program.publicId -ClassId $class.publicId -StudentId $studentUser.publicId

Write-Host "== Creating or reusing active exam package =="
$plan = Ensure-ActivePlan -Name $planName
$subscription = Ensure-Subscription -PlanId $plan.publicId

Write-Host "== Finding the active seeded template =="
$templates = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/score-templates" -Token $script:AdminToken)
$template = $templates | Where-Object { $_.code -eq $templateCode -and "$($_.status)".ToUpperInvariant() -eq "ACTIVE" } | Select-Object -First 1
if ($null -eq $template) {
    throw "Active template '$templateCode' was not found. Run seed-local-template-and-question-bank.ps1 first."
}

Write-Host "== Creating or reusing a published/open exam =="
$sessions = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/sessions" -Token $script:HostToken)
$session = $sessions |
    Where-Object { $_.name -eq $examName -and @("OPEN", "SCHEDULED") -contains "$($_.status)".ToUpperInvariant() } |
    Select-Object -First 1

if ($null -eq $session) {
    $now = (Get-Date).ToUniversalTime()
    $createOpensAt = $now.AddMinutes(2).ToString("yyyy-MM-ddTHH:mm:ssZ")
    $closesAt = $now.AddHours(2).ToString("yyyy-MM-ddTHH:mm:ssZ")
    $session = Invoke-SeedApi -Method POST -Path "/api/v1/sessions/drafts" -Token $script:HostToken -Body @{
        name                 = $examName
        templatePublicId     = $template.publicId
        subscriptionPublicId = $subscription.publicId
        opensAt              = $createOpensAt
        closesAt             = $closesAt
        examMode             = "PRACTICE"
        formMode             = "SHARED_FORM"
        reusePolicy          = "ALLOW"
        capacity             = 1
    }

    # The create contract requires a future opening time. Move the draft back
    # into the current window before generation so the pte-app can start now.
    $openNow = (Get-Date).ToUniversalTime().AddMinutes(-1).ToString("yyyy-MM-ddTHH:mm:ssZ")
    $session = Invoke-SeedApi -Method PATCH -Path "/api/v1/sessions/$($session.publicId)" -Token $script:HostToken -Body @{
        opensAt  = $openNow
        closesAt = $closesAt
    }
}

$sessionStatus = "$($session.status)".ToUpperInvariant()
if ($sessionStatus -eq "DRAFT") {
    $sources = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/sessions/$($session.publicId)/audience-sources" -Token $script:HostToken)
    $source = $sources | Where-Object { "$($_.sourceType)" -eq "CLASS" -and $_.sourcePublicId -eq $class.publicId } | Select-Object -First 1
    if ($null -eq $source) {
        $null = Invoke-SeedApi -Method POST -Path "/api/v1/sessions/$($session.publicId)/audience-sources" -Token $script:HostToken -Body @{
            sourceType    = "CLASS"
            sourcePublicId = $class.publicId
        }
    }

    $preflight = Invoke-SeedApi -Method POST -Path "/api/v1/sessions/$($session.publicId)/preflight" -Token $script:HostToken
    if (-not [bool]$preflight.ready) {
        $issues = (Get-Array $preflight.issues) -join "; "
        throw "Exam preflight is not ready: $issues"
    }
    $generationKey = $seedToken + "-EXAM-V1"
    $null = Invoke-SeedApi -Method POST -Path "/api/v1/sessions/$($session.publicId)/generate?idempotencyKey=$([Uri]::EscapeDataString($generationKey))" -Token $script:HostToken
    $sessionStatus = "READY"
}

if ($sessionStatus -eq "READY") {
    $null = Invoke-SeedApi -Method POST -Path "/api/v1/sessions/$($session.publicId)/publish" -Token $script:HostToken
    $sessionStatus = "SCHEDULED"
}
if ($sessionStatus -eq "SCHEDULED") {
    $session = Invoke-SeedApi -Method POST -Path "/api/v1/sessions/$($session.publicId)/open" -Token $script:HostToken
}
elseif ($sessionStatus -eq "OPEN") {
    $session = Invoke-SeedApi -Method GET -Path "/api/v1/sessions/$($session.publicId)" -Token $script:HostToken
}
else {
    throw "Seed exam '$examName' is in unsupported status '$($session.status)'."
}

Write-Host "== Verifying pte-app capability negotiation =="
$manifest = New-CapabilityManifest
$preflightAttempt = Invoke-SeedApi -Method POST -Path "/api/v1/attempts/preflight" -Token $studentToken -Body @{ sessionPublicId = $session.publicId; capabilityManifest = $manifest }
if (-not [bool]$preflightAttempt.canStart) {
    $missing = (Get-Array $preflightAttempt.missingCapabilities) -join ", "
    $unsupported = (Get-Array $preflightAttempt.unsupportedTasks | ForEach-Object { $_.taskTypeKey }) -join ", "
    throw "pte-app capability preflight failed. Missing: '$missing'. Unsupported tasks: '$unsupported'."
}

$attempt = $null
if (-not $SkipStartAttempt) {
    $attempt = Invoke-SeedApi -Method POST -Path "/api/v1/attempts" -Token $studentToken -Body @{
        sessionPublicId       = $session.publicId
        deviceCheckConfirmed  = $false
        capabilityManifest    = $manifest
    }
}

$enrollments = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/sessions/$($session.publicId)/enrollments" -Token $script:HostToken)
if (-not ($enrollments | Where-Object { $_.studentPublicId -eq $studentUser.publicId })) {
    throw "The published exam does not contain the seeded student enrollment."
}

Write-Host ""
Write-Host "== Local exam seed complete =="
Write-Host "Tenant:       $($tenant.publicId) ($($tenant.code))"
Write-Host "Organization: $($organization.publicId)"
Write-Host "Host login:   $hostEmail"
Write-Host "Student login: $studentEmail"
Write-Host "Program:      $($program.publicId)"
Write-Host "Class:        $($class.publicId)"
Write-Host "Plan:         $($plan.publicId)"
Write-Host "Subscription: $($subscription.publicId)"
Write-Host "Exam:         $($session.publicId) ($($session.status))"
Write-Host "Student can start: true"
if ($null -ne $attempt) {
    Write-Host "Attempt:      $($attempt.attemptPublicId) ($($attempt.attemptStatus))"
}
Write-Host ""
Write-Host "Use the SeedPassword supplied to this script for both host and student."
Write-Host "The script never writes passwords, access tokens, or license codes to disk."
