<#
Generates fresh RSA key material for the three PEM env vars the app hard-
requires outside the dev/local Spring profile:

  IDENTITY_RSA_PRIVATE_KEY_PEM            (JWT signing key)
  ATTEMPT_ENCRYPTION_PRIVATE_KEY_PEM      (STRICT answer-encryption keypair)
  ATTEMPT_ENCRYPTION_PUBLIC_KEY_PEM

See app/src/main/java/com/pte/identity/internal/security/RsaKeyProvider.java
and app/src/main/java/com/pte/attempt/internal/config/EncryptionKeyProvider.java
for exactly how these are parsed (PKCS#8 private key, X.509 SubjectPublicKeyInfo
public key -- the formats openssl already produces by default).

Requires openssl (bundled with Git for Windows at
C:\Program Files\Git\usr\bin\openssl.exe, or already on PATH).

Rotating these invalidates every previously issued JWT and every STRICT
attempt's pinned public key -- only run this against an .env nothing is
actively relying on, or restart the app right after.
#>

param(
    [string]$EnvFile = (Join-Path $PSScriptRoot "..\.env")
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $EnvFile)) {
    throw "$EnvFile does not exist. Copy .env.example to .env first."
}
$EnvFile = (Resolve-Path $EnvFile).Path

function Find-OpenSsl {
    $cmd = Get-Command openssl -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $gitBundled = "C:\Program Files\Git\usr\bin\openssl.exe"
    if (Test-Path $gitBundled) { return $gitBundled }
    throw "openssl.exe not found on PATH or at the default Git for Windows location. Install Git for Windows or OpenSSL for Windows, then re-run."
}

function Set-EnvValue([string]$content, [string]$name, [string]$pemPath) {
    $pem = (Get-Content -Raw $pemPath).Trim()
    $line = "$name=`"$pem`""
    # Matches either this script's own quoted multi-line block from a prior
    # run, or the plain single-line __REQUIRED__ placeholder -- so the script
    # is safe to re-run for key rotation, not just first-time setup.
    $pattern = '(?ms)^' + [regex]::Escape($name) + '=(?:"(?:[^"\\]|\\.)*"|[^\r\n]*)\r?\n?'
    $content = [regex]::Replace($content, $pattern, '')
    return $content.TrimEnd() + "`r`n" + $line + "`r`n"
}

$openssl = Find-OpenSsl
$tmpDir = Join-Path $env:TEMP ("pte-keygen-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tmpDir | Out-Null

try {
    $identityKey = Join-Path $tmpDir "identity-key.pem"
    $attemptPriv = Join-Path $tmpDir "attempt-private.pem"
    $attemptPub  = Join-Path $tmpDir "attempt-public.pem"

    # openssl writes routine progress/status text to stderr even on success
    # (the dot-progress meter, "writing RSA key"). PowerShell 5.1 turns any
    # native-command stderr line into a terminating error under
    # $ErrorActionPreference = 'Stop', regardless of redirection -- so this
    # block relies on $LASTEXITCODE instead, same as the rest of the script.
    $previousEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        Write-Host "Generating identity JWT signing key..."
        & $openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $identityKey -quiet 2>$null
        if ($LASTEXITCODE -ne 0) { throw "openssl failed generating the identity key." }

        Write-Host "Generating attempt answer-encryption keypair..."
        & $openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $attemptPriv -quiet 2>$null
        if ($LASTEXITCODE -ne 0) { throw "openssl failed generating the attempt private key." }
        & $openssl rsa -pubout -in $attemptPriv -out $attemptPub 2>$null
        if ($LASTEXITCODE -ne 0) { throw "openssl failed deriving the attempt public key." }
    }
    finally {
        $ErrorActionPreference = $previousEap
    }

    $backup = "$EnvFile.bak." + (Get-Date -Format "yyyyMMdd-HHmmss")
    Copy-Item $EnvFile $backup
    Write-Host "Backed up existing .env to $backup"

    $content = Get-Content -Raw $EnvFile
    $content = Set-EnvValue $content "IDENTITY_RSA_PRIVATE_KEY_PEM" $identityKey
    $content = Set-EnvValue $content "ATTEMPT_ENCRYPTION_PRIVATE_KEY_PEM" $attemptPriv
    $content = Set-EnvValue $content "ATTEMPT_ENCRYPTION_PUBLIC_KEY_PEM" $attemptPub
    Set-Content -NoNewline -Path $EnvFile -Value $content

    Write-Host "Wrote 3 new key values into $EnvFile"
    Write-Host "Restart the app container to pick them up -- this invalidates every previously issued JWT and every STRICT attempt's pinned public key."
}
finally {
    Remove-Item -Recurse -Force $tmpDir -ErrorAction SilentlyContinue
}
