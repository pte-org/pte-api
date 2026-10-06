# Shared helpers for the seed scripts that talk to the public API edge.
# Dot-source it:  . (Join-Path $PSScriptRoot "lib\seed-common.ps1")
# Needs $BaseUrl in the caller's scope (Set-SeedBaseUrl sets it).

function Import-LocalEnv {
    # Loads PTE_* variables (admin/seed credentials) from .env.local into the process
    # environment unless they are already set. Never prints values.
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path $Path)) { return }
    foreach ($line in Get-Content -Path $Path) {
        if ($line -match '^(PTE_[A-Z_]+)=(.*)$') {
            if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($Matches[1]))) {
                [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim().Trim('"'))
            }
        }
    }
}

function Resolve-Secret {
    param([string]$Value, [Parameter(Mandatory = $true)][string]$Prompt)
    if (-not [string]::IsNullOrWhiteSpace($Value)) { return $Value }
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Get-Array {
    param([AllowNull()][object]$Value)
    if ($null -eq $Value) { return @() }
    return @($Value)
}

function Invoke-SeedApi {
    param(
        [Parameter(Mandatory = $true)][ValidateSet("GET", "POST", "PUT", "PATCH", "DELETE")][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [string]$Token,
        [AllowNull()][object]$Body
    )
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) { $headers["Authorization"] = "Bearer $Token" }
    $request = @{ Uri = "$($script:SeedBaseUrl)$Path"; Method = $Method; Headers = $headers; ErrorAction = "Stop" }
    if ($null -ne $Body) {
        $request["ContentType"] = "application/json"
        $request["Body"] = ($Body | ConvertTo-Json -Depth 20 -Compress)
    }
    try { $response = Invoke-RestMethod @request }
    catch {
        $detail = $_.Exception.Message
        if ($_.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) { $detail = $_.ErrorDetails.Message }
        throw "API $Method $Path failed: $detail"
    }
    if ($null -ne $response -and $response.PSObject.Properties.Name -contains "success" -and -not [bool]$response.success) {
        $message = if ($response.userMessage) { $response.userMessage } elseif ($response.message) { $response.message } else { "Unknown API error" }
        throw "API $Method $Path returned an error: $message"
    }
    if ($null -ne $response -and $response.PSObject.Properties.Name -contains "data") { return $response.data }
    return $response
}

function Set-SeedBaseUrl {
    param([string]$BaseUrl)
    if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
        $BaseUrl = if ([string]::IsNullOrWhiteSpace($env:PTE_BASE_URL)) { "http://localhost:8080" } else { $env:PTE_BASE_URL }
    }
    $script:SeedBaseUrl = $BaseUrl.TrimEnd("/")
}

function Get-SeedToken {
    param([string]$Username, [string]$Password)
    $login = Invoke-SeedApi -Method POST -Path "/api/v1/auth/login" -Body @{ username = $Username; password = $Password }
    if ([string]::IsNullOrWhiteSpace($login.accessToken)) { throw "Login for '$Username' did not return an access token." }
    return $login.accessToken
}
