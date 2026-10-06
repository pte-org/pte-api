# Tenant-level helpers shared by the demo seed scripts (tenant, accounts, class, plan, subscription).
# Dot-source after seed-common.ps1. Every function is idempotent (find, else create) and
# takes the bearer token it must act with.

function Find-UserByEmail {
    param([AllowNull()][object]$Rows, [string]$Email)
    return (Get-Array $Rows | Where-Object { $_.email -eq $Email -or $_.username -eq $Email } | Select-Object -First 1)
}

function Ensure-Tenant {
    param([string]$AdminToken, [string]$Code, [string]$Name, [string]$TaxCode)
    $tenant = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/tenants" -Token $AdminToken) |
        Where-Object { $_.code -eq $Code } | Select-Object -First 1
    if ($null -eq $tenant) {
        $tenant = Invoke-SeedApi -Method POST -Path "/api/v1/tenants" -Token $AdminToken -Body @{
            code = $Code; name = $Name; organizationType = "SCHOOL"; taxCode = $TaxCode
            packageName = "STANDARD"; studentLimit = 50
        }
    }
    if ("$($tenant.status)".ToUpperInvariant() -ne "ACTIVE") {
        $tenant = Invoke-SeedApi -Method POST -Path "/api/v1/tenants/$($tenant.publicId)/reactivate" -Token $AdminToken
    }
    return $tenant
}

function Ensure-TenantUser {
    # Creates the user with the given tenant-scoped role, or resets the password of the existing one
    # so the printed login always works with the current seed password.
    param([string]$Token, [string]$ListPath, [string]$Email, [string]$FullName, [string]$Password,
        [string]$Role, [string]$TenantId, [string]$StudentCode)
    $user = Find-UserByEmail -Rows (Invoke-SeedApi -Method GET -Path $ListPath -Token $Token) -Email $Email
    if ($null -eq $user) {
        $body = @{ email = $Email; fullName = $FullName; password = $Password; roles = @($Role) }
        if ($TenantId) { $body["tenantId"] = $TenantId }
        if ($StudentCode) { $body["studentCode"] = $StudentCode }
        return Invoke-SeedApi -Method POST -Path "/api/v1/users" -Token $Token -Body $body
    }
    $null = Invoke-SeedApi -Method POST -Path "/api/v1/users/$($user.publicId)/reset-password" -Token $Token -Body @{ newPassword = $Password }
    return $user
}

function Ensure-Program {
    param([string]$HostToken, [string]$OrganizationId, [string]$Name)
    $program = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/organizations/$OrganizationId/programs" -Token $HostToken) |
        Where-Object { $_.name -eq $Name } | Select-Object -First 1
    if ($null -eq $program) {
        $program = Invoke-SeedApi -Method POST -Path "/api/v1/organizations/$OrganizationId/programs" -Token $HostToken -Body @{
            name = $Name; description = "Demo data for the 22 task types"
            startDate = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
            endDate = (Get-Date).ToUniversalTime().AddYears(1).ToString("yyyy-MM-dd")
        }
    }
    return $program
}

function Ensure-Class {
    param([string]$HostToken, [string]$OrganizationId, [string]$ProgramId, [string]$Name)
    $class = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/organizations/$OrganizationId/programs/$ProgramId/classes" -Token $HostToken) |
        Where-Object { $_.name -eq $Name } | Select-Object -First 1
    if ($null -eq $class) {
        $class = Invoke-SeedApi -Method POST -Path "/api/v1/organizations/$OrganizationId/programs/$ProgramId/classes" -Token $HostToken -Body @{ name = $Name }
    }
    return $class
}

function Ensure-ClassMembership {
    param([string]$HostToken, [string]$OrganizationId, [string]$ProgramId, [string]$ClassId, [string]$StudentId)
    $membership = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/class-memberships?programPublicId=$ProgramId" -Token $HostToken) |
        Where-Object { $_.classPublicId -eq $ClassId -and $_.studentPublicId -eq $StudentId } | Select-Object -First 1
    if ($null -eq $membership) {
        $membership = Invoke-SeedApi -Method POST -Path "/api/v1/organizations/$OrganizationId/programs/$ProgramId/classes/$ClassId/students" -Token $HostToken -Body @{ studentPublicId = $StudentId }
    }
    return $membership
}

function Ensure-ActivePlan {
    param([string]$AdminToken, [string]$Name)
    $plan = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/plans" -Token $AdminToken) |
        Where-Object { $_.name -eq $Name } | Select-Object -First 1
    if ($null -eq $plan -or "$($plan.status)".ToUpperInvariant() -eq "ARCHIVED") {
        $planName = if ($null -eq $plan) { $Name } else { "$Name $(Get-Date -Format yyyyMMddHHmmss)" }
        $plan = Invoke-SeedApi -Method POST -Path "/api/v1/plans" -Token $AdminToken -Body @{
            name = $planName; description = "Demo exam package"; type = "EXAM_PACKAGE"; price = 0
            currency = "VND"; durationDays = 30; maxStudentsPerSession = 50
        }
    }
    if ("$($plan.status)".ToUpperInvariant() -eq "DRAFT") {
        $plan = Invoke-SeedApi -Method POST -Path "/api/v1/plans/$($plan.publicId)/activation" -Token $AdminToken
    }
    if ("$($plan.status)".ToUpperInvariant() -ne "ACTIVE") { throw "Plan '$($plan.name)' is not active." }
    return $plan
}

function Ensure-Subscription {
    # Returns an ACTIVE, unexpired subscription of the plan; otherwise issues a license code and redeems it.
    param([string]$AdminToken, [string]$HostToken, [string]$PlanId)
    $now = [DateTimeOffset]::UtcNow
    $find = {
        Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/subscriptions" -Token $HostToken) |
            Where-Object { $_.planId -eq $PlanId -and "$($_.status)".ToUpperInvariant() -eq "ACTIVE" -and ([DateTimeOffset]$_.expiresAt) -gt $now.AddDays(2) } |
            Select-Object -First 1
    }
    $subscription = & $find
    if ($null -ne $subscription) { return $subscription }
    $license = Invoke-SeedApi -Method POST -Path "/api/v1/license-codes" -Token $AdminToken -Body @{
        planId = $PlanId; codeExpiresAt = $now.AddDays(30).ToString("yyyy-MM-ddTHH:mm:ssZ")
    }
    $null = Invoke-SeedApi -Method POST -Path "/api/v1/license-code-redemptions" -Token $HostToken -Body @{ code = $license.code }
    $subscription = & $find
    if ($null -eq $subscription) { throw "The license code was redeemed but no active subscription was returned." }
    return $subscription
}
