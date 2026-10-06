[CmdletBinding()]
param(
    [string]$AdminUsername,
    [string]$AdminPassword,
    [string]$PostgresContainer = "pte-postgres",
    [string]$EnvFile
)

# Creates the local PLATFORM_ADMIN directly in Postgres (the API has no self-registration), the
# scripted version of the manual SQL step in README. Needs Docker; the bcrypt hash is computed
# with the httpd:2.4-alpine image (pulled on first use). Idempotent: does nothing when the
# username already exists. The password comes from PTE_ADMIN_PASSWORD (environment or
# .env.local) or a prompt, and is never printed or stored.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\seed-common.ps1")
if ([string]::IsNullOrWhiteSpace($EnvFile)) { $EnvFile = Join-Path (Split-Path -Parent (Split-Path -Parent $scriptDir)) ".env.local" }
Import-LocalEnv -Path $EnvFile
if ([string]::IsNullOrWhiteSpace($AdminUsername)) {
    $AdminUsername = if ([string]::IsNullOrWhiteSpace($env:PTE_ADMIN_USERNAME)) { "admin@test.local" } else { $env:PTE_ADMIN_USERNAME }
}
if ($AdminUsername -notmatch "^[A-Za-z0-9._@+-]+$") { throw "Admin username contains unsupported characters." }

function Invoke-Psql {
    param([string]$Sql)
    $Sql | & docker exec -i $PostgresContainer sh -c 'psql -v ON_ERROR_STOP=1 -t -A -U $POSTGRES_USER -d ${POSTGRES_DB:-pte}'
    if ($LASTEXITCODE -ne 0) { throw "psql failed (exit $LASTEXITCODE)" }
}

$existing = Invoke-Psql "select count(*) from users where username = '$AdminUsername';"
if ([int](@($existing)[0]) -gt 0) {
    Write-Host "Admin '$AdminUsername' already exists - nothing to do."
    return
}

$AdminPassword = Resolve-Secret -Value $(if ($AdminPassword) { $AdminPassword } else { $env:PTE_ADMIN_PASSWORD }) -Prompt "Password for the new platform admin"

# htpasswd needs a non-empty user name; "x:" is stripped. $2y$ and $2a$ are the same bcrypt variant.
$raw = (& docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 x "$AdminPassword" 2>&1 | Out-String).Trim()
if ($raw -notmatch '^x:\$2y\$10\$[./A-Za-z0-9]{53}$') { throw "Could not compute a bcrypt hash (is Docker running?)." }
$hash = '$2a' + $raw.Substring(5)

$sql = "WITH u AS (INSERT INTO users (public_id, username, email, full_name, tenant_id, status, deleted, must_change_password, created_at, updated_at) " +
    "VALUES (gen_random_uuid(), '$AdminUsername', '$AdminUsername', 'Local Platform Admin', NULL, 'ACTIVE', false, false, now(), now()) RETURNING id), " +
    "r AS (INSERT INTO user_roles (user_id, role) SELECT id, 'PLATFORM_ADMIN' FROM u) " +
    "INSERT INTO login_hashes (public_id, user_id, hash, deleted, created_at, updated_at) " +
    "SELECT gen_random_uuid(), id, '$hash', false, now(), now() FROM u;"
$null = Invoke-Psql $sql
Write-Host "Created PLATFORM_ADMIN '$AdminUsername'."
