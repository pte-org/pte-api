[CmdletBinding()]
param(
    [string]$BaseUrl = $(if ([string]::IsNullOrWhiteSpace($env:PTE_BASE_URL)) { "http://localhost:8080" } else { $env:PTE_BASE_URL }),
    [string]$AdminUsername = $(if ([string]::IsNullOrWhiteSpace($env:PTE_ADMIN_USERNAME)) { "admin@test" } else { $env:PTE_ADMIN_USERNAME }),
    [string]$AdminPassword = $env:PTE_ADMIN_PASSWORD,
    [string]$SeedPassword = $env:PTE_SEED_PASSWORD
)

# Local-only fixture for the platform-role expansion. It uses the same
# PLATFORM_ADMIN endpoint as the vendor UI, is idempotent, and never resets or
# deletes unrelated users/data. Passwords are supplied through an environment
# variable or an interactive prompt and are never written to a file/output.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")

function Assert-LocalBaseUrl {
    $parsed = $null
    $valid = [Uri]::TryCreate($BaseUrl, [UriKind]::Absolute, [ref]$parsed)
    if (-not $valid -or $parsed.Scheme -notin @("http", "https") -or
        -not $parsed.IsLoopback -or -not [string]::IsNullOrWhiteSpace($parsed.UserInfo)) {
        throw "This seed script only accepts a loopback API URL such as http://localhost:8080."
    }
}

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

function Invoke-SeedApi {
    param(
        [Parameter(Mandatory = $true)][ValidateSet("GET", "POST", "PATCH")][string]$Method,
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
    if ($null -ne $Body) {
        $request["ContentType"] = "application/json"
        $request["Body"] = ($Body | ConvertTo-Json -Depth 20 -Compress)
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

    if ($null -ne $response -and $response.PSObject.Properties.Name -contains "success" -and
        -not [bool]$response.success) {
        $code = if ($response.code) { " [$($response.code)]" } else { "" }
        $message = if ($response.userMessage) { $response.userMessage }
        elseif ($response.message) { $response.message }
        else { "Unknown API error" }
        throw "API $Method $Path returned an error${code}: $message"
    }

    if ($null -ne $response -and $response.PSObject.Properties.Name -contains "data") {
        return $response.data
    }
    return $response
}

function Get-Token {
    param([string]$Password)

    $login = Invoke-SeedApi -Method POST -Path "/api/v1/auth/login" -Body @{
        username = $AdminUsername
        password = $Password
    }
    if ([string]::IsNullOrWhiteSpace($login.accessToken)) {
        throw "The platform-admin login response did not contain an access token."
    }
    return $login.accessToken
}

function Get-Rows {
    param([AllowNull()][object]$Value)

    if ($null -eq $Value) { return @() }
    if ($Value.PSObject.Properties.Name -contains "content") { return @($Value.content) }
    if ($Value.PSObject.Properties.Name -contains "data" -and
        $Value.PSObject.Properties.Name -contains "meta") { return @($Value.data) }
    if ($Value.PSObject.Properties.Name -contains "items") { return @($Value.items) }
    return @($Value)
}

function Ensure-PlatformUser {
    param(
        [Parameter(Mandatory = $true)][string]$Token,
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][string]$FullName,
        [Parameter(Mandatory = $true)][ValidateSet("PLATFORM_MANAGER", "ACADEMIC_MANAGER", "ACADEMIC_STAFF")][string]$Role,
        [Parameter(Mandatory = $true)][string]$Password
    )

    $page = Invoke-SeedApi -Method GET -Path "/api/v1/platform-users?page=0&size=100" -Token $Token
    $user = Get-Rows $page | Where-Object {
        $_.username -eq $Username -or $_.email -eq $Username
    } | Select-Object -First 1

    if ($null -eq $user) {
        $user = Invoke-SeedApi -Method POST -Path "/api/v1/platform-users" -Token $Token -Body @{
            email    = $Username
            fullName = $FullName
            password = $Password
            roles    = @($Role)
        }
        Write-Host "Created local platform fixture: $Username ($Role)"
        return $user
    }

    $currentRoles = @($user.roles | ForEach-Object { "$($_)".ToUpperInvariant() })
    if ($currentRoles.Count -ne 1 -or $currentRoles[0] -ne $Role) {
        $user = Invoke-SeedApi -Method PATCH -Path "/api/v1/platform-users/$($user.publicId)/roles" -Token $Token -Body @{
            roles = @($Role)
        }
        Write-Host "Reconciled local platform fixture role: $Username ($Role)"
    }

    if ("$($user.status)".ToUpperInvariant() -eq "SUSPENDED") {
        $user = Invoke-SeedApi -Method POST -Path "/api/v1/platform-users/$($user.publicId)/reactivate" -Token $Token
        Write-Host "Reactivated local platform fixture: $Username"
    }
    else {
        Write-Host "Local platform fixture already present: $Username ($Role)"
    }
    return $user
}

Assert-LocalBaseUrl
$AdminPassword = Resolve-Secret -Value $AdminPassword -Prompt "Platform admin password"
$SeedPassword = Resolve-Secret -Value $SeedPassword -Prompt "Password for new local platform fixtures (at least 8 characters)"
if ($SeedPassword.Length -lt 8) {
    throw "The local platform fixture password must contain at least 8 characters to match the API contract."
}
$adminToken = Get-Token -Password $AdminPassword
$adminMe = Invoke-SeedApi -Method GET -Path "/api/v1/auth/me" -Token $adminToken

if ($null -ne $adminMe.tenantId) {
    throw "The supplied account is tenant-scoped; use a PLATFORM_ADMIN account for this seed."
}
$adminRoles = @($adminMe.roles | ForEach-Object { "$($_)".ToUpperInvariant() })
if (-not ($adminRoles -contains "PLATFORM_ADMIN")) {
    throw "The supplied account is not a PLATFORM_ADMIN."
}

$fixtures = @(
    @{ Username = "platform.manager@test"; FullName = "Local Platform Manager"; Role = "PLATFORM_MANAGER" },
    @{ Username = "academic.manager@test"; FullName = "Local Academic Manager"; Role = "ACADEMIC_MANAGER" },
    @{ Username = "academic.staff@test"; FullName = "Local Academic Staff"; Role = "ACADEMIC_STAFF" }
)

foreach ($fixture in $fixtures) {
    $null = Ensure-PlatformUser -Token $adminToken -Username $fixture.Username -FullName $fixture.FullName `
        -Role $fixture.Role -Password $SeedPassword
}

Write-Host "Local platform-role seed complete. No database reset or unrelated data mutation was performed."
