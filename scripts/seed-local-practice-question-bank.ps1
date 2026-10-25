[CmdletBinding()]
param(
    [string]$BaseUrl = $(if ([string]::IsNullOrWhiteSpace($env:PTE_BASE_URL)) { "http://localhost:8080" } else { $env:PTE_BASE_URL }),
    [string]$AdminUsername = $(if ([string]::IsNullOrWhiteSpace($env:PTE_ADMIN_USERNAME)) { "admin@test.local" } else { $env:PTE_ADMIN_USERNAME }),
    [string]$AdminPassword = $env:PTE_ADMIN_PASSWORD,
    [string]$SeedKey = "PTE_PRACTICE",
    [ValidateRange(1, 12)][int]$PerTask = 3
)

# Seeds published practice questions for all 22 scored PTE task types so pte-practice has content to test.
#
# Prerequisites:
# - The local app is running behind the local edge (normally http://localhost:8080) with Cloudinary configured.
# - A PLATFORM_ADMIN (or ACADEMIC_MANAGER) account exists.
#
# Audio/image tasks reuse the sample files in scripts/fixtures, so the recording does not match the prompt text.
# This is local test content only; it is not official PTE material.
# Re-runnable: questions are matched by title and only missing ones are created and published.
# Full mock tests need no seeding: the practice app builds as many as the bank can fill when the list is opened.
#
# Example:
#   $env:PTE_ADMIN_PASSWORD = '<local-admin-password>'
#   .\scripts\seed-local-practice-question-bank.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$FixtureDir = Join-Path $PSScriptRoot "fixtures"

function Resolve-Secret {
    param([string]$Value, [Parameter(Mandatory = $true)][string]$Prompt)
    if (-not [string]::IsNullOrWhiteSpace($Value)) { return $Value }
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Invoke-SeedApi {
    param(
        [Parameter(Mandatory = $true)][ValidateSet("GET", "POST")][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [string]$Token,
        [AllowNull()][object]$Body
    )
    $request = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = @{}; ErrorAction = "Stop" }
    if (-not [string]::IsNullOrWhiteSpace($Token)) { $request.Headers["Authorization"] = "Bearer $Token" }
    if ($null -ne $Body) {
        $request["ContentType"] = "application/json; charset=utf-8"
        $request["Body"] = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 20 -Compress))
    }
    # The per-tenant rate limit (RATE_LIMIT_PER_SECOND) rejects bursts with 429; back off and retry.
    for ($attempt = 1; ; $attempt++) {
        try { $response = Invoke-RestMethod @request; break }
        catch {
            $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
            if ($status -eq 429 -and $attempt -lt 8) { Start-Sleep -Seconds $attempt; continue }
            $detail = $_.Exception.Message
            if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $detail = $_.ErrorDetails.Message }
            throw "API $Method $Path failed: $detail"
        }
    }
    if ($null -ne $response -and $response.PSObject.Properties.Name -contains "data") { return $response.data }
    return $response
}

function Send-PromptMedia {
    param([string]$FilePath, [string]$ContentType, [string]$AssetKind)
    $size = (Get-Item $FilePath).Length
    $grant = Invoke-SeedApi -Method POST -Path "/api/v1/objects" -Token $script:Token -Body @{
        contentType = $ContentType; assetKind = $AssetKind; sizeBytes = $size
    }
    # Same signed multipart fields as vendor-web's QuestionEditorForm.
    $raw = & curl.exe -s -f -X POST $grant.uploadUrl `
        -F "file=@$FilePath;type=$ContentType" -F "api_key=$($grant.apiKey)" -F "timestamp=$($grant.timestamp)" `
        -F "signature=$($grant.signature)" -F "folder=$($grant.folder)" -F "public_id=$($grant.mediaPublicId)"
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($raw)) { throw "Cloudinary upload failed for $FilePath" }
    $uploaded = $raw | ConvertFrom-Json
    $complete = @{
        publicId = $uploaded.public_id; assetId = $uploaded.asset_id; secureUrl = $uploaded.secure_url
        resourceType = $uploaded.resource_type; format = $uploaded.format; bytes = $uploaded.bytes
        version = $uploaded.version; signature = $uploaded.signature
    }
    if ($uploaded.PSObject.Properties.Name -contains "duration" -and $null -ne $uploaded.duration) {
        $complete["durationSeconds"] = [int][Math]::Ceiling([double]$uploaded.duration)
    }
    $null = Invoke-SeedApi -Method POST -Path "/api/v1/objects/$($grant.mediaPublicId)/complete" -Token $script:Token -Body $complete
    return $grant.mediaPublicId
}

function Find-Question {
    param([string]$Title, [string]$TaskKey)
    $page = Invoke-SeedApi -Method GET -Path "/api/v1/questions?q=$([Uri]::EscapeDataString($Title))&size=100" -Token $script:Token
    # PagedResult: { data: [...], meta: {...} }
    $items = if ($null -ne $page -and $page.PSObject.Properties.Name -contains "data") { @($page.data) } else { @() }
    return $items | Where-Object { $_.title -eq $Title -and ($_.taskTypeKey -eq $TaskKey -or $_.pteTaskType -eq $TaskKey) } |
        Select-Object -First 1
}

function New-Options {
    param([object[]]$Rows)
    $index = 0
    return @($Rows | ForEach-Object {
        $option = @{ text = $_[0]; correct = [bool]$_[1]; orderIndex = $index }
        if ($_.Count -gt 2 -and $null -ne $_[2]) { $option[$_[3]] = $_[2] }
        $index++
        $option
    })
}

function Ensure-Question {
    param([string]$TaskKey, [int]$Number, [scriptblock]$BuildFields)
    $title = "$SeedKey - $TaskKey - $Number"
    $question = Find-Question -Title $title -TaskKey $TaskKey
    if ($null -eq $question) {
        $body = @{ pteTaskType = $TaskKey; taskTypeKey = $TaskKey; title = $title; pool = "PRACTICE"
                   availableForPractice = $true; availableForExam = $false }
        # Built only when the question is missing, so a rerun does not upload media again.
        # The sample texts have three variants; later numbers reuse them under a new title so a bank can hold more questions.
        $fields = & $BuildFields (($Number - 1) % 3)
        foreach ($key in $fields.Keys) { $body[$key] = $fields[$key] }
        $question = Invoke-SeedApi -Method POST -Path "/api/v1/questions" -Token $script:Token -Body $body
        Write-Host "  created $title"
    }
    $status = "$($question.status)".ToUpperInvariant()
    if ($status -eq "DRAFT") {
        $question = Invoke-SeedApi -Method POST -Path "/api/v1/questions/$($question.publicId)/publish" -Token $script:Token
    } elseif ($status -ne "APPROVED" -and $status -ne "PUBLISHED") {
        throw "Question '$title' is in status '$($question.status)'; resolve it before rerunning."
    }
}

$script:Token = (Invoke-SeedApi -Method POST -Path "/api/v1/auth/login" -Body @{
    username = $AdminUsername; password = (Resolve-Secret -Value $AdminPassword -Prompt "Platform admin password")
}).accessToken

# Prompt media is uploaded lazily, only when a missing question needs it.
$script:AudioRef = $null
$script:ImageRef = $null
function Get-PromptAudio {
    if ($null -eq $script:AudioRef) {
        $script:AudioRef = Send-PromptMedia -FilePath (Join-Path $FixtureDir "repeat_sentence_sample.wav") -ContentType "audio/wav" -AssetKind "AUDIO_PROMPT"
        Write-Host "  uploaded prompt audio $($script:AudioRef)"
    }
    return $script:AudioRef
}
function Get-PromptImage {
    if ($null -eq $script:ImageRef) {
        $script:ImageRef = Send-PromptMedia -FilePath (Join-Path $FixtureDir "describe_image_sample.png") -ContentType "image/png" -AssetKind "IMAGE_PROMPT"
        Write-Host "  uploaded prompt image $($script:ImageRef)"
    }
    return $script:ImageRef
}

$passages = @(
    "Urban gardens give city residents fresh food, cooler streets and a shared space where neighbours can meet.",
    "Remote work has changed how companies hire, because teams can now include people from many different countries.",
    "Coral reefs protect coastlines from storms, yet rising ocean temperatures are bleaching them at an alarming rate."
)
$sentences = @(
    "The library will close early on Friday for maintenance.",
    "Students must submit their assignments before the deadline.",
    "Regular exercise improves both physical and mental health."
)

$tasks = [ordered]@{
    READ_ALOUD = { param($i) @{ promptText = $passages[$i] } }
    REPEAT_SENTENCE = { param($i) @{ audioPromptRef = (Get-PromptAudio); referenceAnswerText = $sentences[$i] } }
    DESCRIBE_IMAGE = { param($i) @{ imagePromptRef = (Get-PromptImage); promptText = "Describe the image in detail." } }
    RE_TELL_LECTURE = { param($i) @{ audioPromptRef = (Get-PromptAudio); referenceAnswerText = $passages[$i] } }
    ANSWER_SHORT_QUESTION = { param($i) @{ audioPromptRef = (Get-PromptAudio); correctAnswerText = @("thermometer", "calendar", "dictionary")[$i] } }
    RESPOND_TO_A_SITUATION = { param($i) @{ audioPromptRef = (Get-PromptAudio); promptText = @(
        "Your friend missed the bus to an important interview. Give them advice.",
        "Your manager asks you to work this weekend, but you have plans. Respond politely.",
        "A classmate asks to borrow your notes the night before the exam. Reply to them.")[$i] } }
    SUMMARIZE_GROUP_DISCUSSION = { param($i) @{ audioPromptRef = (Get-PromptAudio); referenceAnswerText = $passages[$i] } }
    SUMMARIZE_WRITTEN_TEXT = { param($i) @{ promptText = ($passages[$i] + " " + $passages[($i + 1) % 3]); minWordCount = 5; maxWordCount = 75 } }
    WRITE_ESSAY = { param($i) @{ promptText = @(
        "Some people believe technology makes us less social. Discuss both views and give your opinion.",
        "Should university education be free for everyone? Support your answer with examples.",
        "Is it better to live in a big city or in the countryside? Explain your view.")[$i]; minWordCount = 200; maxWordCount = 300 } }
    MC_READING_SINGLE = { param($i) @{ promptText = $passages[$i] + " What is the main idea of the passage?"
        options = New-Options @(@("It describes a benefit and a challenge of the topic.", $true),
            @("It argues the topic is no longer relevant.", $false), @("It lists historical dates.", $false)) } }
    MC_READING_MULTIPLE = { param($i) @{ promptText = $passages[$i] + " Which statements are supported by the passage?"
        options = New-Options @(@("The passage names at least one benefit.", $true), @("The passage mentions a change or risk.", $true),
            @("The passage gives exact statistics.", $false), @("The passage rejects the topic entirely.", $false)) } }
    RE_ORDER_PARAGRAPHS = { param($i) @{ options = New-Options @(
        @("Researchers first noticed the pattern in 2010.", $false), @("They then collected data from twelve cities.", $false),
        @("The results confirmed their early observation.", $false), @("Finally, they published their findings in a journal.", $false)) } }
    FILL_IN_THE_BLANKS_DRAG_AND_DROP = { param($i) @{ promptText = "Good planning can {{0}} uncertainty and make a project more {{1}}."
        options = New-Options @(@("reduce", $true, 0, "correctGapIndex"), @("predictable", $true, 1, "correctGapIndex"),
            @("increase", $false), @("random", $false)) } }
    FILL_IN_THE_BLANKS_DROPDOWN = { param($i) @{ promptText = "Public transport is {{0}} when services are frequent and {{1}} to use."
        options = New-Options @(@("popular", $true, 0, "blankIndex"), @("empty", $false, 0, "blankIndex"),
            @("easy", $true, 1, "blankIndex"), @("hard", $false, 1, "blankIndex")) } }
    SUMMARIZE_SPOKEN_TEXT = { param($i) @{ audioPromptRef = (Get-PromptAudio); promptText = "Summarize the lecture in 50 to 70 words."; minWordCount = 50; maxWordCount = 70 } }
    MC_LISTENING_SINGLE = { param($i) @{ audioPromptRef = (Get-PromptAudio); promptText = "What is the speaker mainly talking about?"
        options = New-Options @(@("A practical announcement.", $true), @("A sports result.", $false), @("A cooking recipe.", $false)) } }
    MC_LISTENING_MULTIPLE = { param($i) @{ audioPromptRef = (Get-PromptAudio); promptText = "Which points does the speaker make?"
        options = New-Options @(@("There is a schedule change.", $true), @("Listeners should plan ahead.", $true),
            @("The event is cancelled forever.", $false), @("Tickets are free.", $false)) } }
    FILL_IN_THE_BLANKS_TYPE_IN = { param($i) @{ audioPromptRef = (Get-PromptAudio); promptText = "The speaker explains why {{0}} practice improves {{1}} memory."
        correctAnswerText = '["regular","long-term"]' } }
    HIGHLIGHT_CORRECT_SUMMARY = { param($i) @{ audioPromptRef = (Get-PromptAudio); promptText = "Which summary best matches the recording?"
        options = New-Options @(@("The speaker gives a short notice about a change.", $true),
            @("The speaker tells a long personal story.", $false), @("The speaker compares two countries.", $false)) } }
    SELECT_MISSING_WORD = { param($i) @{ audioPromptRef = (Get-PromptAudio); promptText = "Select the word that completes the recording."
        options = New-Options @(@("maintenance", $true), @("holiday", $false), @("celebration", $false)) } }
    HIGHLIGHT_INCORRECT_WORDS = { param($i) @{ audioPromptRef = (Get-PromptAudio)
        promptText = "The library will open early on Monday for maintenance."; correctAnswerText = "[3,6]" } }
    WRITE_FROM_DICTATION = { param($i) @{ audioPromptRef = (Get-PromptAudio); correctAnswerText = $sentences[$i] } }
}

Write-Host "== Creating and publishing $PerTask practice question(s) per task type =="
foreach ($taskKey in $tasks.Keys) {
    for ($number = 1; $number -le $PerTask; $number++) {
        Ensure-Question -TaskKey $taskKey -Number $number -BuildFields $tasks[$taskKey]
    }
}

Write-Host ""
Write-Host "== Practice question bank seed complete: $($tasks.Count) task types x $PerTask =="
