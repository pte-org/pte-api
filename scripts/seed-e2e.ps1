<#
.SYNOPSIS
  Idempotent end-to-end seed script for pte-api local dev/testing
  (plans/phat-speaking-api-e2e-verify, extended by plans/phat-speaking-audio-
  prompt-e2e Phase 3 for Repeat Sentence).

  Seeds, via the gateway's real public REST API only (never in-process, never
  direct DB access beyond the one bootstrap step below): one tenant, one
  HOST_ADMIN, one STUDENT, one published READ_ALOUD question/blueprint/
  snapshot, one published REPEAT_SENTENCE question/blueprint/snapshot (with
  a real uploaded audio file as its audioPromptRef), and one OPEN scheduling
  session per question with the student enrolled in both.

.DESCRIPTION
  Talks ONLY to the gateway (http://localhost:8080 by default) — the exact
  same path a real client (pte-app) would use. Safe to re-run any number of
  times: every resource is looked up via a real GET/list call before being
  created, not just cached in a local state file.

  PREREQUISITE (one-time, cannot be done via API — pte-api has no
  self-registration endpoint): bootstrap a PLATFORM_ADMIN account directly
  via SQL against the `iam` Postgres database
  (localhost:5432/iam, user iam_svc / password iam_dev_pw per
  pte-api/docker-compose.yml).

  1. Generate a BCrypt hash for your chosen bootstrap password. `htpasswd`
     is NOT bundled with every Git Bash install (confirmed missing on at
     least one dev machine this was verified against) — the reliable,
     nothing-extra-to-install option is Docker itself (already required for
     the rest of this workflow):

       docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 "" 'Password123!'

     (If your Git Bash does have `htpasswd` on PATH, `htpasswd -bnBC 10 ""
     'Password123!' | cut -d: -f2` works too — both produce the same
     BCrypt format.) Copy the printed hash (starts with $2a$/$2b$/$2y$,
     strip the leading ":" if using the Docker form) — the SQL below uses
     the placeholder <BCRYPT_HASH>.

  2. Run this SQL once, e.g. `psql -h localhost -U iam_svc -d iam`:

       INSERT INTO users (public_id, email, full_name, tenant_id, status, deleted, created_at, updated_at)
       VALUES (gen_random_uuid(), 'admin@test.local', 'Bootstrap Admin', NULL, 'ACTIVE', false, now(), now())
       RETURNING id;
       -- capture the printed id (an integer, e.g. 1) — use it as <USER_ID> below.
       -- CRITICAL: user_roles.user_id and login_hashes.user_id both FK to
       -- users.id (this internal bigint), NEVER users.public_id, even though
       -- public_id is this codebase's convention for cross-references
       -- everywhere else (confirmed against User.java / LoginHash.java /
       -- BaseEntity.java — this is the one deliberate exception).

       INSERT INTO user_roles (user_id, role) VALUES (<USER_ID>, 'PLATFORM_ADMIN');

       INSERT INTO login_hashes (public_id, user_id, hash, deleted, created_at, updated_at)
       VALUES (gen_random_uuid(), <USER_ID>, '<BCRYPT_HASH>', false, now(), now());

  If you bootstrapped with an email/password other than the defaults below,
  pass -BootstrapAdminEmail / -BootstrapAdminPassword.

  KNOWN LIMITATION (see plans/phat-speaking-api-e2e-verify Phase 2 Risks):
  every resource this script creates is re-discoverable via a real GET/list
  call EXCEPT the published snapshot(s) and the Repeat Sentence audio media
  object — SnapshotPublishService.publish() has no server-side idempotency
  guard (every call mints a new version) and there is no "get snapshot by
  blueprint" lookup endpoint anywhere in authoring; MediaController likewise
  exposes no GET-by-id lookup at all. So each of these publicIds is recorded
  in a local state file (seed-e2e.state.json, git-ignored, next to this
  script) the moment it's created, and THAT is the only way to recover them
  on a later run. If this state file is deleted or drifts out of sync with
  the actual database (e.g. after `docker compose down -v` without deleting
  the state file too), this script cannot reconcile the two — delete both
  together and re-run from scratch.

  The Repeat Sentence audio fixture itself (fixtures/repeat_sentence_sample.wav,
  committed to this repo) is a short synthesized tone, not a real spoken
  sentence — genuinely audible (unlike pte-app's own silent
  assets/audio/*.wav mock fixtures, per plans/phat-windows-audio-playback-fix),
  which is all Phase 4's manual walkthrough needs to confirm the app plays
  real server-resolved audio rather than silence. Swap in an actual
  recording via -RepeatSentenceAudioFixturePath if you want to verify
  intelligible speech specifically.

.NOTES
  Hardcoded to localhost by default and refuses to run against anything
  else — there is no Spring @Profile gate for an external script, so this
  guard is the equivalent safety net (plan.md Risks).
#>

[CmdletBinding()]
param(
    [string]$GatewayBaseUrl = 'http://localhost:8080',
    [string]$BootstrapAdminEmail = 'admin@test.local',
    [string]$BootstrapAdminPassword = 'Password123!',
    [string]$TenantName = 'E2E Seed Tenant',
    [string]$HostEmail = 'host.e2e@test.local',
    [string]$HostPassword = 'Password123!',
    [string]$StudentEmail = 'student.e2e@test.local',
    [string]$StudentPassword = 'Password123!',
    [string]$QuestionTitle = 'E2E Seed - Read Aloud',
    [string]$BlueprintName = 'E2E Seed Blueprint - Read Aloud',
    [string]$SessionName = 'E2E Seed Session - Read Aloud',
    # Repeat Sentence gets its own question/blueprint/session, parallel to
    # (not merged into) Read Aloud's above — keeps the already-verified Read
    # Aloud flow's composition/ordering untouched (plans/phat-speaking-audio-
    # prompt-e2e Phase 3).
    [string]$RepeatSentenceQuestionTitle = 'E2E Seed - Repeat Sentence',
    [string]$RepeatSentenceBlueprintName = 'E2E Seed Blueprint - Repeat Sentence',
    [string]$RepeatSentenceSessionName = 'E2E Seed Session - Repeat Sentence',
    # Defaults to the committed fixture (fixtures/repeat_sentence_sample.wav
    # — a short, genuinely audible synthesized tone, not one of the silent
    # assets/audio/*.wav fixtures pte-app already has) if left blank; see
    # this file's default-resolution below (needs $PSScriptRoot, which is
    # more reliably read after the param block than inside its default
    # expression across PowerShell versions).
    [string]$RepeatSentenceAudioFixturePath = ''
)

$ErrorActionPreference = 'Stop'

if ($GatewayBaseUrl -notmatch 'localhost|127\.0\.0\.1') {
    throw "Refusing to run against a non-local gateway URL: $GatewayBaseUrl (this script is dev/local-only)"
}

if ([string]::IsNullOrWhiteSpace($RepeatSentenceAudioFixturePath)) {
    $RepeatSentenceAudioFixturePath = Join-Path $PSScriptRoot 'fixtures/repeat_sentence_sample.wav'
}

# ---------------------------------------------------------------------------
# State file — the ONLY recovery path for the published snapshot's publicId
# (see header comment). Everything else is re-derived from live API calls
# below and the state file is just a convenience cache for those.
# ---------------------------------------------------------------------------
$StateFilePath = Join-Path $PSScriptRoot 'seed-e2e.state.json'

if (Test-Path $StateFilePath) {
    $State = Get-Content -Path $StateFilePath -Raw | ConvertFrom-Json
} else {
    $State = [PSCustomObject]@{}
}

# Backfill any property this script's current schema expects but an older
# state file (from before that property existed — e.g. one saved by
# phat-speaking-api-e2e-verify's original Read Aloud-only version) doesn't
# have. Setting a property PowerShell doesn't already know about on a
# PSCustomObject throws ("cannot be found on this object") rather than
# silently creating it, unlike a Hashtable — so every property this script
# might ever set must be guaranteed to exist here first, once, regardless
# of which version of the file was loaded.
foreach ($prop in @(
    'tenantPublicId', 'hostPublicId', 'studentPublicId', 'questionPublicId', 'blueprintPublicId',
    'snapshotPublicId', 'sessionPublicId',
    'repeatSentenceAudioMediaPublicId', 'repeatSentenceQuestionPublicId', 'repeatSentenceBlueprintPublicId',
    'repeatSentenceSnapshotPublicId', 'repeatSentenceSessionPublicId'
)) {
    if (-not (Get-Member -InputObject $State -Name $prop -MemberType NoteProperty)) {
        $State | Add-Member -NotePropertyName $prop -NotePropertyValue $null
    }
}

function Save-State {
    $State | ConvertTo-Json -Depth 10 | Set-Content -Path $StateFilePath -Encoding UTF8
}

function Write-Step([string]$Message) {
    Write-Host "[seed-e2e] $Message" -ForegroundColor Cyan
}

function Write-Ok([string]$Message) {
    Write-Host "[seed-e2e] $Message" -ForegroundColor Green
}

function Find-First($Items, [string]$Property, $Value) {
    foreach ($item in $Items) {
        if ($item.$Property -eq $Value) { return $item }
    }
    return $null
}

function Invoke-Api {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [string]$Token,
        $Body
    )
    $uri = "$GatewayBaseUrl$Path"
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    try {
        if ($null -ne $Body) {
            $jsonBody = $Body | ConvertTo-Json -Depth 10
            return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody
        }
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $statusCode = [int]$resp.StatusCode
            $errorBody = $null
            try {
                $stream = $resp.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                $errorBody = $reader.ReadToEnd()
            } catch {
                # Response stream not readable — fall through with $errorBody still $null.
            }
            Write-Host "[seed-e2e] FAILED: $Method $Path -> HTTP $statusCode" -ForegroundColor Red
            if ($errorBody) { Write-Host $errorBody -ForegroundColor Red }
        } else {
            # No HTTP response at all — connection-level failure (gateway not
            # reachable, refused, DNS, etc.), not an HTTP error status.
            Write-Host "[seed-e2e] FAILED: $Method $Path -> no HTTP response ($($_.Exception.Message))" -ForegroundColor Red
        }
        throw
    }
}

Write-Step "Target gateway: $GatewayBaseUrl"

# ---------------------------------------------------------------------------
# 1. Bootstrap admin login
# ---------------------------------------------------------------------------
Write-Step "Logging in as bootstrap PLATFORM_ADMIN ($BootstrapAdminEmail)..."
$loginResp = Invoke-Api -Method Post -Path '/api/iam/auth/login' -Body @{
    email    = $BootstrapAdminEmail
    password = $BootstrapAdminPassword
}
$platformToken = $loginResp.data.accessToken
Write-Ok "Logged in as bootstrap admin."

# ---------------------------------------------------------------------------
# 2. Tenant (PLATFORM_ADMIN only)
# ---------------------------------------------------------------------------
Write-Step "Resolving tenant '$TenantName'..."
$tenantsResp = Invoke-Api -Method Get -Path '/api/admin/tenants' -Token $platformToken
$tenant = Find-First -Items $tenantsResp.data -Property 'name' -Value $TenantName
if ($null -eq $tenant) {
    Write-Step "Tenant not found - creating..."
    $createTenantResp = Invoke-Api -Method Post -Path '/api/admin/tenants' -Token $platformToken -Body @{
        name             = $TenantName
        organizationType = 'SCHOOL'
        packageName      = 'E2E_SEED'
        studentLimit     = 50
    }
    $tenant = $createTenantResp.data
    Write-Ok "Created tenant $($tenant.publicId)"
} else {
    Write-Ok "Found existing tenant $($tenant.publicId)"
}
$State.tenantPublicId = $tenant.publicId
Save-State

# ---------------------------------------------------------------------------
# 3. Host admin + student users (PLATFORM_ADMIN can create either; list via
#    the platform-scoped by-tenant lookup, which is the only lookup a
#    PLATFORM_ADMIN caller has for an arbitrary tenant's users — GET /users
#    itself is caller-tenant-scoped and returns nothing for a platform
#    caller).
# ---------------------------------------------------------------------------
Write-Step "Resolving users for tenant $($tenant.publicId)..."
$tenantUsersResp = Invoke-Api -Method Get -Path "/api/iam/users/by-tenant/$($tenant.publicId)" -Token $platformToken
$tenantUsers = $tenantUsersResp.data

$hostUser = Find-First -Items $tenantUsers -Property 'email' -Value $HostEmail
if ($null -eq $hostUser) {
    Write-Step "Host admin not found - creating $HostEmail..."
    $createHostResp = Invoke-Api -Method Post -Path '/api/iam/users' -Token $platformToken -Body @{
        email    = $HostEmail
        fullName = 'E2E Seed Host'
        password = $HostPassword
        roles    = @('HOST_ADMIN')
        tenantId = $tenant.publicId
    }
    $hostUser = $createHostResp.data
    Write-Ok "Created host admin $($hostUser.publicId)"
} else {
    Write-Ok "Found existing host admin $($hostUser.publicId)"
}
$State.hostPublicId = $hostUser.publicId

$studentUser = Find-First -Items $tenantUsers -Property 'email' -Value $StudentEmail
if ($null -eq $studentUser) {
    Write-Step "Student not found - creating $StudentEmail..."
    $createStudentResp = Invoke-Api -Method Post -Path '/api/iam/users' -Token $platformToken -Body @{
        email    = $StudentEmail
        fullName = 'E2E Seed Student'
        password = $StudentPassword
        roles    = @('STUDENT')
        tenantId = $tenant.publicId
    }
    $studentUser = $createStudentResp.data
    Write-Ok "Created student $($studentUser.publicId)"
} else {
    Write-Ok "Found existing student $($studentUser.publicId)"
}
$State.studentPublicId = $studentUser.publicId
Save-State

# ---------------------------------------------------------------------------
# 4. Host login — authoring's Question/Blueprint/Snapshot endpoints and every
#    scheduling endpoint require HOST_ADMIN/HOST_AUTHOR specifically; there
#    is no role hierarchy configured anywhere in this backend, so the
#    platform token from step 1 does NOT satisfy those checks.
# ---------------------------------------------------------------------------
Write-Step "Logging in as host admin ($HostEmail)..."
$hostLoginResp = Invoke-Api -Method Post -Path '/api/iam/auth/login' -Body @{
    email    = $HostEmail
    password = $HostPassword
}
$hostToken = $hostLoginResp.data.accessToken
Write-Ok "Logged in as host admin."

# ---------------------------------------------------------------------------
# 5. READ_ALOUD question (visibility PRIVATE — SHARED/platform-bank
#    visibility is PLATFORM_AUTHOR-only to write, and this script only ever
#    authenticates as HOST_ADMIN for authoring calls).
# ---------------------------------------------------------------------------
Write-Step "Resolving question '$QuestionTitle'..."
$questionsResp = Invoke-Api -Method Get -Path '/api/authoring/questions' -Token $hostToken
$question = Find-First -Items $questionsResp.data -Property 'title' -Value $QuestionTitle
if ($null -eq $question) {
    Write-Step "Question not found - creating..."
    $createQuestionResp = Invoke-Api -Method Post -Path '/api/authoring/questions' -Token $hostToken -Body @{
        pteTaskType = 'READ_ALOUD'
        visibility  = 'PRIVATE'
        title       = $QuestionTitle
        promptText  = 'The government announced today that funding for public transportation would increase by fifteen percent over the next fiscal year, aiming to reduce congestion in major urban centers.'
    }
    $question = $createQuestionResp.data
    Write-Ok "Created question $($question.publicId)"
} else {
    Write-Ok "Found existing question $($question.publicId)"
}
$State.questionPublicId = $question.publicId
Save-State

# ---------------------------------------------------------------------------
# 6. Blueprint containing that question
# ---------------------------------------------------------------------------
Write-Step "Resolving blueprint '$BlueprintName'..."
$blueprintsResp = Invoke-Api -Method Get -Path '/api/authoring/blueprints' -Token $hostToken
$blueprint = Find-First -Items $blueprintsResp.data -Property 'name' -Value $BlueprintName
if ($null -eq $blueprint) {
    Write-Step "Blueprint not found - creating..."
    $createBlueprintResp = Invoke-Api -Method Post -Path '/api/authoring/blueprints' -Token $hostToken -Body @{
        name  = $BlueprintName
        items = @(
            @{ questionPublicId = $question.publicId; section = 'SPEAKING'; orderIndex = 0 }
        )
    }
    $blueprint = $createBlueprintResp.data
    Write-Ok "Created blueprint $($blueprint.publicId)"
} else {
    Write-Ok "Found existing blueprint $($blueprint.publicId) (status: $($blueprint.status))"
}
$State.blueprintPublicId = $blueprint.publicId
Save-State

# ---------------------------------------------------------------------------
# 7. Published snapshot — see header comment: NOT re-derivable via any API
#    call once published, only via this script's own state file.
# ---------------------------------------------------------------------------
Write-Step "Resolving published snapshot for blueprint $($blueprint.publicId)..."
if ($blueprint.status -eq 'PUBLISHED') {
    if ($State.snapshotPublicId) {
        Write-Ok "Blueprint already published; using snapshot $($State.snapshotPublicId) from state file."
    } else {
        throw "Blueprint $($blueprint.publicId) is already PUBLISHED but this script's state file has no " +
              "record of its snapshot publicId, and there is no API to look it up after the fact " +
              "(SnapshotPublishService has no 'get snapshot by blueprint' endpoint). " +
              "Delete seed-e2e.state.json AND reset the pte-api database together, then re-run from scratch."
    }
} else {
    Write-Step "Blueprint not yet published - publishing..."
    $publishResp = Invoke-Api -Method Post -Path "/api/authoring/blueprints/$($blueprint.publicId)/publish" -Token $hostToken
    $State.snapshotPublicId = $publishResp.data.publicId
    Save-State
    Write-Ok "Published snapshot $($State.snapshotPublicId)"
}
$snapshotPublicId = $State.snapshotPublicId

# ---------------------------------------------------------------------------
# 8. Scheduling session — opensAt/closesAt MUST be computed relative to now
#    on every run (never a fixed literal): CreateSessionRequest.opensAt is
#    @Future-validated, so a hardcoded past-by-now value would permanently
#    break the very first create() call with no get-or-create path ever
#    reaching far enough to notice.
# ---------------------------------------------------------------------------
Write-Step "Resolving session '$SessionName'..."
$sessionsResp = Invoke-Api -Method Get -Path '/api/scheduling/sessions' -Token $hostToken
$session = Find-First -Items $sessionsResp.data -Property 'name' -Value $SessionName
if ($null -eq $session) {
    Write-Step "Session not found - creating..."
    $opensAt = (Get-Date).ToUniversalTime().AddMinutes(5)
    $closesAt = $opensAt.AddHours(2)
    $createSessionResp = Invoke-Api -Method Post -Path '/api/scheduling/sessions' -Token $hostToken -Body @{
        name             = $SessionName
        snapshotPublicId = $snapshotPublicId
        opensAt          = $opensAt.ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
        closesAt         = $closesAt.ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
        # PRACTICE, not the null-default MOCK_TEST: ExamPolicy.mockTestDefault()
        # sets deviceCheckRequired=true, which would reject every attempt
        # start with DeviceCheckRequiredException (409) since pte-app has no
        # real device-check flow wired into this pre-exam path yet (see
        # ExamAttemptRepositoryImpl's deviceCheckConfirmed comment). This
        # plan verifies the Read Aloud task path, not that specific gate.
        examMode         = 'PRACTICE'
    }
    $session = $createSessionResp.data
    Write-Ok "Created session $($session.publicId) (opens $($opensAt.ToString('u')))"
} else {
    Write-Ok "Found existing session $($session.publicId) (status: $($session.status))"
}
$State.sessionPublicId = $session.publicId
Save-State

# ---------------------------------------------------------------------------
# 9. Composition — setComposition() clears-then-reapplies server-side, so
#    it's safe to always call, but we skip it when already correct to keep
#    console output honest about what actually changed.
# ---------------------------------------------------------------------------
$hasReadAloudComposition = $false
if ($session.composition) {
    foreach ($item in $session.composition) {
        if ($item.taskType -eq 'READ_ALOUD') { $hasReadAloudComposition = $true }
    }
}
if ($hasReadAloudComposition) {
    Write-Ok "Composition already includes READ_ALOUD."
} else {
    Write-Step "Setting composition (READ_ALOUD)..."
    Invoke-Api -Method Put -Path "/api/scheduling/sessions/$($session.publicId)/composition" -Token $hostToken -Body @{
        items = @(
            # No override — real production default (40s). The actual root
            # cause of ResponseWindowExpiredException rejecting genuine
            # on-time submissions was a zero-grace deadline check server-side
            # (TimerState.isResponseWindowExpired), now fixed with a 15s
            # grace window — the real upload+complete+submit round-trip
            # measured well under 1s on localhost, so 15s is ample margin.
            # Testing with the real default timing (not an inflated
            # override) is the correct final verification.
            @{ taskType = 'READ_ALOUD'; section = 'SPEAKING'; orderIndex = 0; timingOverrideSeconds = $null; maxPlayCount = $null }
        )
    } | Out-Null
    Write-Ok "Composition set."
}

# ---------------------------------------------------------------------------
# 10. Open — open() is a trivial idempotent status setter server-side, but
#     gate it anyway to keep output honest.
# ---------------------------------------------------------------------------
if ($session.status -eq 'OPEN') {
    Write-Ok "Session already OPEN."
} else {
    Write-Step "Opening session..."
    Invoke-Api -Method Post -Path "/api/scheduling/sessions/$($session.publicId)/open" -Token $hostToken | Out-Null
    Write-Ok "Session opened."
}

# ---------------------------------------------------------------------------
# 11. Enrollment
# ---------------------------------------------------------------------------
Write-Step "Resolving enrollment for student $($studentUser.publicId)..."
$enrollmentsResp = Invoke-Api -Method Get -Path "/api/scheduling/sessions/$($session.publicId)/enrollments" -Token $hostToken
$enrollment = Find-First -Items $enrollmentsResp.data -Property 'studentPublicId' -Value $studentUser.publicId
if ($null -eq $enrollment) {
    Write-Step "Student not enrolled - enrolling..."
    Invoke-Api -Method Post -Path "/api/scheduling/sessions/$($session.publicId)/enrollments" -Token $hostToken -Body @{
        studentPublicId = $studentUser.publicId
    } | Out-Null
    Write-Ok "Enrolled student."
} else {
    Write-Ok "Student already enrolled."
}

# ---------------------------------------------------------------------------
# 12. Repeat Sentence audio media — uploaded via the same 3-step presigned
#     flow pte-app's own MediaUploadCoordinator uses for a student's
#     recorded answer (POST /objects -> PUT the bytes directly to MinIO ->
#     POST .../complete), just authenticated as the host instead of a
#     student. Like the published snapshot below, MediaController exposes
#     no GET-by-id lookup at all, so an already-uploaded media's publicId is
#     NOT re-derivable via any API call once uploaded — the state file is
#     the only recovery path, same limitation and same mitigation as the
#     snapshot (see header comment and step 7).
# ---------------------------------------------------------------------------
if (-not (Test-Path $RepeatSentenceAudioFixturePath)) {
    throw "Repeat Sentence audio fixture not found: $RepeatSentenceAudioFixturePath " +
          "(expected fixtures/repeat_sentence_sample.wav next to this script, or pass -RepeatSentenceAudioFixturePath)"
}

Write-Step "Resolving Repeat Sentence audio media..."
if ($State.repeatSentenceAudioMediaPublicId) {
    Write-Ok "Using previously-uploaded media $($State.repeatSentenceAudioMediaPublicId) from state file."
} else {
    Write-Step "No prior upload recorded - uploading $RepeatSentenceAudioFixturePath..."
    $requestUploadResp = Invoke-Api -Method Post -Path '/api/media/objects' -Token $hostToken -Body @{
        contentType = 'audio/wav'
    }
    $mediaPublicId = $requestUploadResp.data.mediaPublicId
    $uploadUrl = $requestUploadResp.data.uploadUrl

    try {
        # -UseBasicParsing: Invoke-WebRequest otherwise instantiates IE's
        # HTML parsing engine even for a plain PUT with no HTML response to
        # parse, which throws "NonInteractive mode" here since nothing runs
        # IE's one-time first-launch config in this shell.
        Invoke-WebRequest -Method Put -Uri $uploadUrl -InFile $RepeatSentenceAudioFixturePath `
            -ContentType 'audio/wav' -UseBasicParsing | Out-Null
    } catch {
        Write-Host "[seed-e2e] FAILED: PUT $uploadUrl -> $($_.Exception.Message)" -ForegroundColor Red
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            Write-Host $_.ErrorDetails.Message -ForegroundColor Red
        }
        throw
    }

    Invoke-Api -Method Post -Path "/api/media/objects/$mediaPublicId/complete" -Token $hostToken | Out-Null
    $State.repeatSentenceAudioMediaPublicId = $mediaPublicId
    Save-State
    Write-Ok "Uploaded and completed media $mediaPublicId"
}
$repeatSentenceAudioMediaPublicId = $State.repeatSentenceAudioMediaPublicId

# ---------------------------------------------------------------------------
# 13. Repeat Sentence question — audioPromptRef is the only required field
#     beyond the generic ones (REPEAT_SENTENCE's PteTaskType flags: no
#     promptText/options/correctAnswer/wordCount required).
# ---------------------------------------------------------------------------
Write-Step "Resolving question '$RepeatSentenceQuestionTitle'..."
$rsQuestionsResp = Invoke-Api -Method Get -Path '/api/authoring/questions' -Token $hostToken
$repeatSentenceQuestion = Find-First -Items $rsQuestionsResp.data -Property 'title' -Value $RepeatSentenceQuestionTitle
if ($null -eq $repeatSentenceQuestion) {
    Write-Step "Question not found - creating..."
    $createRsQuestionResp = Invoke-Api -Method Post -Path '/api/authoring/questions' -Token $hostToken -Body @{
        pteTaskType    = 'REPEAT_SENTENCE'
        visibility     = 'PRIVATE'
        title          = $RepeatSentenceQuestionTitle
        audioPromptRef = $repeatSentenceAudioMediaPublicId
    }
    $repeatSentenceQuestion = $createRsQuestionResp.data
    Write-Ok "Created question $($repeatSentenceQuestion.publicId)"
} else {
    Write-Ok "Found existing question $($repeatSentenceQuestion.publicId)"
}
$State.repeatSentenceQuestionPublicId = $repeatSentenceQuestion.publicId
Save-State

# ---------------------------------------------------------------------------
# 14. Blueprint containing that question
# ---------------------------------------------------------------------------
Write-Step "Resolving blueprint '$RepeatSentenceBlueprintName'..."
$rsBlueprintsResp = Invoke-Api -Method Get -Path '/api/authoring/blueprints' -Token $hostToken
$repeatSentenceBlueprint = Find-First -Items $rsBlueprintsResp.data -Property 'name' -Value $RepeatSentenceBlueprintName
if ($null -eq $repeatSentenceBlueprint) {
    Write-Step "Blueprint not found - creating..."
    $createRsBlueprintResp = Invoke-Api -Method Post -Path '/api/authoring/blueprints' -Token $hostToken -Body @{
        name  = $RepeatSentenceBlueprintName
        items = @(
            @{ questionPublicId = $repeatSentenceQuestion.publicId; section = 'SPEAKING'; orderIndex = 0 }
        )
    }
    $repeatSentenceBlueprint = $createRsBlueprintResp.data
    Write-Ok "Created blueprint $($repeatSentenceBlueprint.publicId)"
} else {
    Write-Ok "Found existing blueprint $($repeatSentenceBlueprint.publicId) (status: $($repeatSentenceBlueprint.status))"
}
$State.repeatSentenceBlueprintPublicId = $repeatSentenceBlueprint.publicId
Save-State

# ---------------------------------------------------------------------------
# 15. Published snapshot — same not-re-derivable-via-API limitation as step 7.
# ---------------------------------------------------------------------------
Write-Step "Resolving published snapshot for blueprint $($repeatSentenceBlueprint.publicId)..."
if ($repeatSentenceBlueprint.status -eq 'PUBLISHED') {
    if ($State.repeatSentenceSnapshotPublicId) {
        Write-Ok "Blueprint already published; using snapshot $($State.repeatSentenceSnapshotPublicId) from state file."
    } else {
        throw "Blueprint $($repeatSentenceBlueprint.publicId) is already PUBLISHED but this script's state file has " +
              "no record of its snapshot publicId. Delete seed-e2e.state.json AND reset the pte-api database " +
              "together, then re-run from scratch."
    }
} else {
    Write-Step "Blueprint not yet published - publishing..."
    $rsPublishResp = Invoke-Api -Method Post -Path "/api/authoring/blueprints/$($repeatSentenceBlueprint.publicId)/publish" -Token $hostToken
    $State.repeatSentenceSnapshotPublicId = $rsPublishResp.data.publicId
    Save-State
    Write-Ok "Published snapshot $($State.repeatSentenceSnapshotPublicId)"
}
$repeatSentenceSnapshotPublicId = $State.repeatSentenceSnapshotPublicId

# ---------------------------------------------------------------------------
# 16. Scheduling session
# ---------------------------------------------------------------------------
Write-Step "Resolving session '$RepeatSentenceSessionName'..."
$rsSessionsResp = Invoke-Api -Method Get -Path '/api/scheduling/sessions' -Token $hostToken
$repeatSentenceSession = Find-First -Items $rsSessionsResp.data -Property 'name' -Value $RepeatSentenceSessionName
if ($null -eq $repeatSentenceSession) {
    Write-Step "Session not found - creating..."
    $rsOpensAt = (Get-Date).ToUniversalTime().AddMinutes(5)
    $rsClosesAt = $rsOpensAt.AddHours(2)
    $createRsSessionResp = Invoke-Api -Method Post -Path '/api/scheduling/sessions' -Token $hostToken -Body @{
        name             = $RepeatSentenceSessionName
        snapshotPublicId = $repeatSentenceSnapshotPublicId
        opensAt          = $rsOpensAt.ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
        closesAt         = $rsClosesAt.ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
        # PRACTICE for the same reason as the Read Aloud session (step 8) —
        # no real device-check flow wired into pte-app's pre-exam path yet.
        examMode         = 'PRACTICE'
    }
    $repeatSentenceSession = $createRsSessionResp.data
    Write-Ok "Created session $($repeatSentenceSession.publicId) (opens $($rsOpensAt.ToString('u')))"
} else {
    Write-Ok "Found existing session $($repeatSentenceSession.publicId) (status: $($repeatSentenceSession.status))"
}
$State.repeatSentenceSessionPublicId = $repeatSentenceSession.publicId
Save-State

# ---------------------------------------------------------------------------
# 17. Composition
# ---------------------------------------------------------------------------
$hasRepeatSentenceComposition = $false
if ($repeatSentenceSession.composition) {
    foreach ($item in $repeatSentenceSession.composition) {
        if ($item.taskType -eq 'REPEAT_SENTENCE') { $hasRepeatSentenceComposition = $true }
    }
}
if ($hasRepeatSentenceComposition) {
    Write-Ok "Composition already includes REPEAT_SENTENCE."
} else {
    Write-Step "Setting composition (REPEAT_SENTENCE)..."
    Invoke-Api -Method Put -Path "/api/scheduling/sessions/$($repeatSentenceSession.publicId)/composition" -Token $hostToken -Body @{
        items = @(
            # No override — real production default (prepSeconds 10 /
            # responseSeconds 15, per task-timing.json, added this same
            # plan's Phase 1 alongside the audio-presign fix).
            @{ taskType = 'REPEAT_SENTENCE'; section = 'SPEAKING'; orderIndex = 0; timingOverrideSeconds = $null; maxPlayCount = $null }
        )
    } | Out-Null
    Write-Ok "Composition set."
}

# ---------------------------------------------------------------------------
# 18. Open
# ---------------------------------------------------------------------------
if ($repeatSentenceSession.status -eq 'OPEN') {
    Write-Ok "Session already OPEN."
} else {
    Write-Step "Opening session..."
    Invoke-Api -Method Post -Path "/api/scheduling/sessions/$($repeatSentenceSession.publicId)/open" -Token $hostToken | Out-Null
    Write-Ok "Session opened."
}

# ---------------------------------------------------------------------------
# 19. Enrollment
# ---------------------------------------------------------------------------
Write-Step "Resolving enrollment for student $($studentUser.publicId)..."
$rsEnrollmentsResp = Invoke-Api -Method Get -Path "/api/scheduling/sessions/$($repeatSentenceSession.publicId)/enrollments" -Token $hostToken
$rsEnrollment = Find-First -Items $rsEnrollmentsResp.data -Property 'studentPublicId' -Value $studentUser.publicId
if ($null -eq $rsEnrollment) {
    Write-Step "Student not enrolled - enrolling..."
    Invoke-Api -Method Post -Path "/api/scheduling/sessions/$($repeatSentenceSession.publicId)/enrollments" -Token $hostToken -Body @{
        studentPublicId = $studentUser.publicId
    } | Out-Null
    Write-Ok "Enrolled student."
} else {
    Write-Ok "Student already enrolled."
}

Write-Host ''
Write-Ok '================================================================'
Write-Ok 'Seed complete.'
Write-Ok "  Tenant:           $($tenant.publicId) ($TenantName)"
Write-Ok "  Host admin:       $($hostUser.publicId) ($HostEmail / $HostPassword)"
Write-Ok "  Student:          $($studentUser.publicId) ($StudentEmail / $StudentPassword)"
Write-Ok "  Read Aloud session:      $($session.publicId) ($SessionName)"
Write-Ok "  Repeat Sentence session: $($repeatSentenceSession.publicId) ($RepeatSentenceSessionName)"
Write-Ok "  sessionPublicId for pte-app's SessionEntryPage (Read Aloud):      $($session.publicId)"
Write-Ok "  sessionPublicId for pte-app's SessionEntryPage (Repeat Sentence): $($repeatSentenceSession.publicId)"
Write-Ok '================================================================'
