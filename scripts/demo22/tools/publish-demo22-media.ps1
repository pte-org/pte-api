[CmdletBinding()]
param(
    [string]$EnvFile,
    [string]$MediaDir,
    [string]$Folder = "pte/demo22"
)

# ONE-TIME step for the person who owns the Cloudinary account (teammates do not run this).
# Uploads the self-generated demo media (see generate-demo22-media.ps1) to Cloudinary with
# PUBLIC delivery so every machine can play it from the committed seed, then records the
# resulting urls in scripts/demo22/data/demo22-media.json and rebuilds the seed.
#
# Credentials come from CLOUDINARY_* in the env file (default .env.local) and are never
# printed or written anywhere. Re-running overwrites the same public ids (idempotent).

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$apiRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $scriptDir))
$seedDir = Join-Path $apiRoot "scripts\demo22\data"
if ([string]::IsNullOrWhiteSpace($EnvFile)) { $EnvFile = Join-Path $apiRoot ".env.local" }
if ([string]::IsNullOrWhiteSpace($MediaDir)) { $MediaDir = Join-Path $env:TEMP "demo22-media" }

function Get-EnvValue {
    param([string]$Name)
    $line = Get-Content -Path $EnvFile | Where-Object { $_ -match "^$Name=" } | Select-Object -First 1
    if (-not $line) { throw "$Name is missing from $EnvFile" }
    return ($line -replace "^$Name=", "").Trim().Trim('"')
}

$cloud = Get-EnvValue "CLOUDINARY_CLOUD_NAME"
$apiKey = Get-EnvValue "CLOUDINARY_API_KEY"
$apiSecret = Get-EnvValue "CLOUDINARY_API_SECRET"

$questions = (Get-Content -Raw -Path (Join-Path $seedDir "demo22-questions.json") | ConvertFrom-Json).questions
$files = @($questions | ForEach-Object {
        foreach ($kind in "audio", "image") {
            $media = $_.$kind
            if ($null -ne $media -and $media.localFile) { $media.localFile }
        }
    } | Sort-Object -Unique)
if ($files.Count -eq 0) { throw "No local media listed in demo22-questions.json" }

function Get-Sha1Hex {
    param([string]$Text)
    $sha1 = [System.Security.Cryptography.SHA1]::Create()
    try {
        return (($sha1.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text)) | ForEach-Object { $_.ToString("x2") }) -join "")
    }
    finally { $sha1.Dispose() }
}

$mediaMapPath = Join-Path $seedDir "demo22-media.json"
$mediaMap = [ordered]@{}
foreach ($file in $files) {
    $path = Join-Path $MediaDir $file
    if (-not (Test-Path $path)) { throw "Missing $path - run scripts\demo22\tools\generate-demo22-media.ps1 first" }
    $resourceType = if ($file.EndsWith(".png")) { "image" } else { "video" }
    $publicId = [IO.Path]::GetFileNameWithoutExtension($file)
    $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    # Signed params, alphabetical, excluding file/api_key/resource_type.
    $signature = Get-Sha1Hex "folder=$Folder&public_id=$publicId&timestamp=$timestamp$apiSecret"

    $json = & curl.exe --silent --show-error --fail-with-body --max-time 300 `
        -F "file=@$path" -F "api_key=$apiKey" -F "timestamp=$timestamp" `
        -F "signature=$signature" -F "public_id=$publicId" -F "folder=$Folder" `
        "https://api.cloudinary.com/v1_1/$cloud/$resourceType/upload"
    if ($LASTEXITCODE -ne 0) { throw "Upload of $file failed (curl exit $LASTEXITCODE): $json" }

    $result = $json | ConvertFrom-Json
    $duration = $null
    if ($result.PSObject.Properties.Name -contains "duration") { $duration = [int][Math]::Ceiling([double]$result.duration) }
    $mediaMap[$file] = [ordered]@{
        url             = $result.secure_url
        publicId        = $result.public_id
        assetId         = $result.asset_id
        sizeBytes       = [long]$result.bytes
        durationSeconds = $duration
    }
    Write-Host ("uploaded {0,-24} -> {1}" -f $file, $result.public_id)
}

[IO.File]::WriteAllText($mediaMapPath, (($mediaMap | ConvertTo-Json -Depth 5) + "`n"), (New-Object Text.UTF8Encoding($false)))
Write-Host "Wrote $mediaMapPath"

& node (Join-Path $scriptDir "build-demo22-questions.js")
if ($LASTEXITCODE -ne 0) { throw "build-demo22-questions.js failed" }
