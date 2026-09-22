[CmdletBinding()]
param(
    [string]$BaseUrl = $(if ([string]::IsNullOrWhiteSpace($env:PTE_BASE_URL)) { "http://localhost:8080" } else { $env:PTE_BASE_URL }),
    [string]$AdminUsername = $(if ([string]::IsNullOrWhiteSpace($env:PTE_ADMIN_USERNAME)) { "admin@test.local" } else { $env:PTE_ADMIN_USERNAME }),
    [string]$AdminPassword = $env:PTE_ADMIN_PASSWORD,
    [string]$SeedKey = "PTE_LOCAL"
)

# Prerequisites:
# - The local app is running behind the local edge (normally http://localhost:8080).
# - A PLATFORM_ADMIN already exists; this script only provisions catalog data.
# - Custom template activation is enabled in the local environment.
#
# Example:
#   $env:PTE_ADMIN_PASSWORD = '<local-admin-password>'
#   .\scripts\seed-local-template-and-question-bank.ps1

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

function Get-AdminToken {
    param([string]$Password)

    $login = Invoke-SeedApi -Method POST -Path "/api/v1/auth/login" -Body @{
        username = $AdminUsername
        password = $Password
    }
    if ([string]::IsNullOrWhiteSpace($login.accessToken)) {
        throw "The login response did not contain an access token."
    }
    return $login.accessToken
}

function Get-Array {
    param([AllowNull()][object]$Value)
    if ($null -eq $Value) {
        return @()
    }
    return @($Value)
}

function Get-TaskTypeRows {
    $page = Invoke-SeedApi -Method GET -Path "/api/v1/task-types?activeOnly=true&limit=100" -Token $script:AdminToken
    if ($page.PSObject.Properties.Name -contains "items") {
        return Get-Array $page.items
    }
    return Get-Array $page
}

function Ensure-CatalogTaskType {
    param([Parameter(Mandatory = $true)][pscustomobject]$Definition)

    $catalog = Get-TaskTypeRows
    $row = $catalog |
        Where-Object { $_.taskTypeKey -eq $Definition.Key -or $_.code -eq $Definition.Key -or $_.taskTypeCode -eq $Definition.Key } |
        Select-Object -First 1
    if ($null -eq $row) {
        $row = Invoke-SeedApi -Method POST -Path "/api/v1/task-types" -Token $script:AdminToken -Body @{
            taskTypeKey    = $Definition.Key
            displayName    = $Definition.DisplayName
            shortName      = $Definition.ShortName
            section        = $Definition.Section
            screenKey      = $Definition.ScreenKey
            contractVersion = 1
            displayOrder   = $Definition.DisplayOrder
            active         = $true
        }
        Write-Host "  created catalog task type $($Definition.Key) ($($row.publicId))"
    }
    if (-not [bool]$row.active) {
        throw "Required task type '$($Definition.Key)' exists but is inactive. Reactivate it in the catalog before rerunning the seed."
    }
    return $row
}

function Get-QuestionByTitle {
    param(
        [string]$Title,
        [string]$TaskTypeKey
    )

    $query = [Uri]::EscapeDataString($Title)
    $questions = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/questions?q=$query" -Token $script:AdminToken)
    return $questions |
        Where-Object {
            $_.title -eq $Title -and
            ($_.taskTypeKey -eq $TaskTypeKey -or $_.pteTaskType -eq $TaskTypeKey)
        } |
        Select-Object -First 1
}

function Ensure-PublishedQuestion {
    param(
        [Parameter(Mandatory = $true)][pscustomobject]$Definition,
        [Parameter(Mandatory = $true)][int]$Index
    )

    $title = "$SeedKey - $($Definition.Key) - $Index"
    $question = Get-QuestionByTitle -Title $title -TaskTypeKey $Definition.Key

    if ($null -eq $question) {
        $body = @{
            pteTaskType  = $Definition.Key
            taskTypeKey  = $Definition.Key
            title        = $title
            promptText   = $Definition.Prompt
        }
        if ($Definition.Key -eq "READ_ALOUD") {
            $body["referenceAnswerText"] = $Definition.ReferenceAnswer
        }
        else {
            $body["options"] = @(
                @{ text = "The statement is supported by the passage."; correct = $true; orderIndex = 0 },
                @{ text = "The statement contradicts the passage."; correct = $false; orderIndex = 1 },
                @{ text = "The passage does not discuss the topic."; correct = $false; orderIndex = 2 },
                @{ text = "The statement is unrelated to the topic."; correct = $false; orderIndex = 3 }
            )
        }
        $question = Invoke-SeedApi -Method POST -Path "/api/v1/questions" -Token $script:AdminToken -Body $body
        Write-Host "  created question $title ($($question.publicId))"
    }

    $status = "$($question.status)".ToUpperInvariant()
    if ($status -eq "APPROVED" -or $status -eq "PUBLISHED") {
        return $question
    }
    if ($status -eq "ARCHIVED") {
        $question = Invoke-SeedApi -Method POST -Path "/api/v1/questions/$($question.publicId)/unarchive" -Token $script:AdminToken
        $status = "$($question.status)".ToUpperInvariant()
    }
    if ($status -eq "DRAFT") {
        $question = Invoke-SeedApi -Method POST -Path "/api/v1/questions/$($question.publicId)/publish" -Token $script:AdminToken
    }
    elseif ($status -ne "APPROVED" -and $status -ne "PUBLISHED") {
        throw "Seed question '$title' is in unsupported status '$($question.status)'. Resolve it before rerunning the seed."
    }
    return $question
}

function New-DesiredTemplateItems {
    return @(
        @{
            taskType        = "READ_ALOUD"
            taskTypeKey     = "READ_ALOUD"
            section         = "SPEAKING"
            sequence        = 1
            minCount        = 1
            maxCount        = 1
            prepSeconds     = 35
            responseSeconds = 40
            speakingWeight  = 100
            writingWeight   = 0
            readingWeight   = 0
            listeningWeight = 0
        },
        @{
            taskType        = "MC_READING_SINGLE"
            taskTypeKey     = "MC_READING_SINGLE"
            section         = "READING"
            sequence        = 2
            minCount        = 1
            maxCount        = 1
            prepSeconds     = 0
            responseSeconds = 60
            speakingWeight  = 0
            writingWeight   = 0
            readingWeight   = 100
            listeningWeight = 0
        }
    )
}

function Test-TemplateShape {
    param([pscustomobject]$Template)

    if ($null -eq $Template -or "$($Template.templatePolicy)".ToUpperInvariant() -ne "CUSTOM") {
        return $false
    }
    $keys = Get-Array $Template.items | ForEach-Object { if ($_.taskTypeKey) { $_.taskTypeKey } else { $_.taskType } }
    return (@($keys | Sort-Object) -join ",") -eq "MC_READING_SINGLE,READ_ALOUD"
}

function Get-OrCreateDraftTemplate {
    param([string]$Code)

    $templates = Get-Array (Invoke-SeedApi -Method GET -Path "/api/v1/score-templates" -Token $script:AdminToken)
    $template = $templates |
        Where-Object { $_.code -eq $Code } |
        Sort-Object -Property version -Descending |
        Select-Object -First 1

    if ($null -eq $template) {
        return Invoke-SeedApi -Method POST -Path "/api/v1/score-templates" -Token $script:AdminToken -Body @{
            code          = $Code
            name          = "PTE local practice template"
            templatePolicy = "CUSTOM"
        }
    }

    $status = "$($template.status)".ToUpperInvariant()
    if ($status -eq "ACTIVE" -and (Test-TemplateShape $template)) {
        return $template
    }
    if ($status -eq "PENDING_APPROVAL") {
        $template = Invoke-SeedApi -Method POST -Path "/api/v1/score-templates/$($template.publicId)/approve" -Token $script:AdminToken
        $status = "$($template.status)".ToUpperInvariant()
    }
    if ($status -eq "RETIRED") {
        return Invoke-SeedApi -Method POST -Path "/api/v1/score-templates/$($template.publicId)/clone" -Token $script:AdminToken
    }
    if ($status -eq "DRAFT") {
        return $template
    }
    if ($status -eq "ACTIVE") {
        return Invoke-SeedApi -Method POST -Path "/api/v1/score-templates" -Token $script:AdminToken -Body @{
            code           = $Code
            name           = "PTE local practice template"
            templatePolicy = "CUSTOM"
        }
    }
    throw "Template '$Code' is in unsupported status '$($template.status)'."
}

$AdminPassword = Resolve-Secret -Value $AdminPassword -Prompt "Platform admin password"
$script:AdminToken = Get-AdminToken -Password $AdminPassword
$seedToken = (($SeedKey -replace "[^A-Za-z0-9_]", "_").ToUpperInvariant())
$seedToken = $seedToken.Substring(0, [Math]::Min(52, $seedToken.Length))
$templateCode = $seedToken + "_TEMPLATE"

$definitions = @(
    [pscustomobject]@{
        Key             = "READ_ALOUD"
        DisplayName     = "Read Aloud"
        ShortName       = "RA"
        Section         = "SPEAKING"
        ScreenKey       = "READ_ALOUD_V1"
        DisplayOrder    = 2
        Prompt          = "The platform supports flexible English practice for learners in many different settings."
        ReferenceAnswer = "The platform supports flexible English practice for learners in many different settings."
    },
    [pscustomobject]@{
        Key             = "MC_READING_SINGLE"
        DisplayName     = "Multiple-choice Reading (Single)"
        ShortName       = "MCS-R"
        Section         = "READING"
        ScreenKey       = "MC_READING_SINGLE_V1"
        DisplayOrder    = 11
        Prompt          = "The platform supports flexible English practice for learners in many different settings. Which statement is supported by the passage?"
        ReferenceAnswer = $null
    }
)

Write-Host "== Checking active task-type catalog =="
foreach ($definition in $definitions) {
    $row = Ensure-CatalogTaskType -Definition $definition
    Write-Host "  $($definition.Key): screen=$($row.screenKey), contract=$($row.contractVersion)"
}

Write-Host "== Creating or reusing two published questions per task type =="
foreach ($definition in $definitions) {
    1..2 | ForEach-Object { $null = Ensure-PublishedQuestion -Definition $definition -Index $_ }
}

Write-Host "== Creating or reusing the custom template draft =="
$template = Get-OrCreateDraftTemplate -Code $templateCode
if ("$($template.status)".ToUpperInvariant() -ne "ACTIVE") {
    $template = Invoke-SeedApi -Method PUT -Path "/api/v1/score-templates/$($template.publicId)/items" -Token $script:AdminToken -Body @{
        name           = "PTE local practice template"
        templatePolicy = "CUSTOM"
        items          = New-DesiredTemplateItems
    }
    $template = Invoke-SeedApi -Method POST -Path "/api/v1/score-templates/$($template.publicId)/activate" -Token $script:AdminToken
}

Write-Host ""
Write-Host "== Template/question-bank seed complete =="
Write-Host "Template code: $($template.code)"
Write-Host "Template id:   $($template.publicId)"
Write-Host "Template v:    $($template.version) ($($template.status))"
Write-Host "Task types:    READ_ALOUD, MC_READING_SINGLE"
Write-Host "Questions:     2 per task type, published and shared"
Write-Host ""
Write-Host "Next: run scripts/seed-local-exam-ready.ps1 with the same -SeedKey."
