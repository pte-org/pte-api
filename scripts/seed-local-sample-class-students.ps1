[CmdletBinding()]
param(
    [string]$BaseUrl = $(if ([string]::IsNullOrWhiteSpace($env:PTE_BASE_URL)) { "http://localhost:8080" } else { $env:PTE_BASE_URL }),
    [string]$HostEmail = $(if ([string]::IsNullOrWhiteSpace($env:PTE_HOST_EMAIL)) { "host.pte-local@test.local" } else { $env:PTE_HOST_EMAIL }),
    [string]$HostPassword = $env:PTE_HOST_PASSWORD
)

# Adds a readable sample roster to the local host's tenant: 3 programs, 5 classes and
# 33 students (30 placed in classes, 3 left without a class on purpose so the
# "Chưa thuộc lớp nào" filter has something to show).
#
# Re-runnable: programs, classes, students (matched by email) and class memberships that
# already exist are reused. Generated student passwords are NOT printed or stored; use the
# host's reset-password action when you need to log in as one of them.
#
# Example:
#   $env:PTE_HOST_PASSWORD = '<local-host-password>'
#   .\scripts\seed-local-sample-roster.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")

$Programs = @(
    @{
        Name        = "Luyện thi PTE Academic 50+ (Khóa 2026)"
        Description = "Khóa nền tảng cho học viên cần đạt tối thiểu 50 điểm PTE Academic."
        StartDate   = "2026-09-01"
        EndDate     = "2027-02-28"
        Classes     = @(
            @{ Name = "PTE-50A - Sáng thứ 2-4-6"; Students = @(
                @("Nguyễn Văn An", "an.nguyen"), @("Trần Thị Bích Ngọc", "ngoc.tran"), @("Lê Hoàng Minh", "minh.le"),
                @("Phạm Thu Hà", "ha.pham"), @("Võ Quốc Bảo", "bao.vo"), @("Đặng Khánh Linh", "linh.dang")) },
            @{ Name = "PTE-50B - Tối thứ 3-5"; Students = @(
                @("Bùi Anh Tuấn", "tuan.bui"), @("Hoàng Mai Phương", "phuong.hoang"), @("Đỗ Gia Huy", "huy.do"),
                @("Ngô Thanh Trúc", "truc.ngo"), @("Dương Minh Khôi", "khoi.duong"), @("Lý Ngọc Hân", "han.ly"),
                @("Phan Đức Thịnh", "thinh.phan")) }
        )
    },
    @{
        Name        = "Luyện thi PTE Academic 65+ (Khóa 2026)"
        Description = "Khóa nâng cao cho học viên hướng tới 65 điểm trở lên, hồ sơ du học và định cư."
        StartDate   = "2026-09-15"
        EndDate     = "2027-03-15"
        Classes     = @(
            @{ Name = "PTE-65A - Sáng thứ 3-5-7"; Students = @(
                @("Vũ Thị Thanh Tâm", "tam.vu"), @("Nguyễn Hữu Phước", "phuoc.nguyen"), @("Trịnh Diệu Anh", "anh.trinh"),
                @("Lương Quang Vinh", "vinh.luong"), @("Mai Thị Hồng Nhung", "nhung.mai"), @("Châu Tấn Lộc", "loc.chau")) },
            @{ Name = "PTE-65B - Tối thứ 2-4"; Students = @(
                @("Huỳnh Bảo Châu", "chau.huynh"), @("Tạ Minh Quân", "quan.ta"), @("Đinh Thị Yến Nhi", "nhi.dinh"),
                @("Cao Văn Sơn", "son.cao"), @("Lâm Khả Hân", "han.lam"), @("Trương Gia Bảo", "bao.truong")) }
        )
    },
    @{
        Name        = "PTE Cấp tốc 79+ (Khóa hè 2026)"
        Description = "Khóa cấp tốc 8 tuần, luyện đề và chấm chữa chi tiết cho mục tiêu 79 điểm."
        StartDate   = "2026-10-05"
        EndDate     = "2026-12-14"
        Classes     = @(
            @{ Name = "PTE-79 - Cấp tốc cuối tuần"; Students = @(
                @("Nguyễn Thị Kim Oanh", "oanh.nguyen"), @("Phùng Anh Dũng", "dung.phung"), @("Hồ Thanh Tùng", "tung.ho"),
                @("La Phương Thảo", "thao.la"), @("Kiều Minh Đạt", "dat.kieu")) }
        )
    }
)

# Registered with the platform but not placed in any class yet.
$UnassignedStudents = @(
    @("Lê Thị Cẩm Tú", "tu.le"),
    @("Nguyễn Đình Khang", "khang.nguyen"),
    @("Trần Bảo Ngân", "ngan.tran")
)

function Resolve-Secret {
    param([string]$Value, [Parameter(Mandatory = $true)][string]$Prompt)
    if (-not [string]::IsNullOrWhiteSpace($Value)) { return $Value }
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][ValidateSet("GET", "POST")][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [string]$Token,
        [AllowNull()][object]$Body
    )
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) { $headers["Authorization"] = "Bearer $Token" }
    $request = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; ErrorAction = "Stop" }
    if ($null -ne $Body) {
        # Explicit UTF-8 bytes: Windows PowerShell 5.1 would otherwise mangle Vietnamese diacritics.
        $request["ContentType"] = "application/json; charset=utf-8"
        $request["Body"] = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 20 -Compress))
    }
    try { $response = Invoke-RestMethod @request }
    catch {
        $detail = $_.Exception.Message
        if ($_.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) { $detail = $_.ErrorDetails.Message }
        throw "API $Method $Path failed: $detail"
    }
    if ($null -ne $response -and $response.PSObject.Properties.Name -contains "success" -and -not [bool]$response.success) {
        throw "API $Method $Path returned an error: $($response.userMessage)"
    }
    if ($null -ne $response -and $response.PSObject.Properties.Name -contains "data") { return $response.data }
    return $response
}

function Get-Array {
    param([AllowNull()][object]$Value)
    if ($null -eq $Value) { return @() }
    return @($Value)
}

function ConvertTo-AsciiSlug {
    param([string]$Value)
    $normalized = $Value.Normalize([Text.NormalizationForm]::FormD)
    $builder = [Text.StringBuilder]::new()
    foreach ($char in $normalized.ToCharArray()) {
        if ([Globalization.CharUnicodeInfo]::GetUnicodeCategory($char) -ne [Globalization.UnicodeCategory]::NonSpacingMark) {
            [void]$builder.Append($char)
        }
    }
    return ($builder.ToString().Replace([char]0x0111, "d").Replace([char]0x0110, "D").ToLowerInvariant() -replace "[^a-z0-9.]", "")
}

$HostPassword = Resolve-Secret -Value $HostPassword -Prompt "Password of $HostEmail"
$login = Invoke-Api -Method POST -Path "/api/v1/auth/login" -Body @{ username = $HostEmail; password = $HostPassword }
$token = $login.accessToken
if ([string]::IsNullOrWhiteSpace($token)) { throw "Login for '$HostEmail' did not return an access token." }

$organization = Get-Array (Invoke-Api -Method GET -Path "/api/v1/organizations" -Token $token) | Select-Object -First 1
if ($null -eq $organization) { throw "The host has no organization." }
$orgId = $organization.publicId

# --- students (one bulk call for everyone who does not exist yet) -------------------------
$allStudents = @()
$code = 0
foreach ($program in $Programs) {
    foreach ($class in $program.Classes) {
        foreach ($student in $class.Students) { $allStudents += , $student }
    }
}
foreach ($student in $UnassignedStudents) { $allStudents += , $student }

$existingUsers = Get-Array (Invoke-Api -Method GET -Path "/api/v1/users" -Token $token)
$byEmail = @{}
foreach ($user in $existingUsers) { if ($user.email) { $byEmail[$user.email.ToLowerInvariant()] = $user } }

$rows = @()
foreach ($student in $allStudents) {
    $code++
    $email = "$((ConvertTo-AsciiSlug $student[1]))@student.example.vn"
    if (-not $byEmail.ContainsKey($email)) {
        $rows += @{ email = $email; fullName = $student[0]; studentCode = ("HV26{0:D4}" -f $code) }
    }
}
if ($rows.Count -gt 0) {
    $result = Invoke-Api -Method POST -Path "/api/v1/users/bulk" -Token $token -Body @{ rows = $rows }
    Write-Host "Students created: $(@($result.created).Count), skipped: $(@($result.skipped).Count)"
    foreach ($skipped in @($result.skipped)) { Write-Warning "Skipped $($skipped.email): $($skipped.reason)" }
    $existingUsers = Get-Array (Invoke-Api -Method GET -Path "/api/v1/users" -Token $token)
    $byEmail = @{}
    foreach ($user in $existingUsers) { if ($user.email) { $byEmail[$user.email.ToLowerInvariant()] = $user } }
}
else {
    Write-Host "All students already exist."
}

# --- programs, classes, memberships --------------------------------------------------------
$existingPrograms = Get-Array (Invoke-Api -Method GET -Path "/api/v1/organizations/$orgId/programs" -Token $token)
foreach ($program in $Programs) {
    $created = $existingPrograms | Where-Object { $_.name -eq $program.Name } | Select-Object -First 1
    if ($null -eq $created) {
        $created = Invoke-Api -Method POST -Path "/api/v1/organizations/$orgId/programs" -Token $token -Body @{
            name = $program.Name; description = $program.Description; startDate = $program.StartDate; endDate = $program.EndDate
        }
        Write-Host "Program: $($program.Name)"
    }
    $classesPath = "/api/v1/organizations/$orgId/programs/$($created.publicId)/classes"
    $existingClasses = Get-Array (Invoke-Api -Method GET -Path $classesPath -Token $token)
    $memberships = Get-Array (Invoke-Api -Method GET -Path "/api/v1/class-memberships?programPublicId=$($created.publicId)" -Token $token)

    foreach ($class in $program.Classes) {
        $createdClass = $existingClasses | Where-Object { $_.name -eq $class.Name } | Select-Object -First 1
        if ($null -eq $createdClass) {
            $createdClass = Invoke-Api -Method POST -Path $classesPath -Token $token -Body @{ name = $class.Name }
            Write-Host "  Class: $($class.Name)"
        }
        $assigned = @($memberships | Where-Object { $_.classPublicId -eq $createdClass.publicId } | ForEach-Object { $_.studentPublicId })
        $toAssign = @()
        foreach ($student in $class.Students) {
            $email = "$((ConvertTo-AsciiSlug $student[1]))@student.example.vn"
            $user = $byEmail[$email]
            if ($null -ne $user -and $assigned -notcontains $user.publicId) { $toAssign += $user.publicId }
        }
        if ($toAssign.Count -gt 0) {
            $null = Invoke-Api -Method POST -Path "$classesPath/$($createdClass.publicId)/students/bulk" -Token $token -Body @{ studentPublicIds = $toAssign }
            Write-Host "    Assigned $($toAssign.Count) students"
        }
    }
}

Write-Host "Done. Open /host/students to review the roster."
