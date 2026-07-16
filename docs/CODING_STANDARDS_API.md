# Aptis API Coding Standards
**Spring Boot 4.1 / Java / JPA / Lombok**

**Last Updated:** 2026-06-24  
**Audience:** Java/Spring Boot developers, AI code generators  
**Status:** Production Ready

---

## Table of Contents
1. [Core Principles](#core-principles)
2. [Naming Conventions](#naming-conventions)
3. [File Structure & Organization](#file-structure--organization)
4. [SOLID Principles in Spring Boot](#solid-principles-in-spring-boot)
5. [Constants Management](#constants-management)
6. [Exception Handling](#exception-handling)
7. [API Response Wrapper](#api-response-wrapper)
8. [Transaction & Performance](#transaction--performance)
9. [Secrets Management](#secrets-management)
10. [Multi-Tenant Isolation](#multi-tenant-isolation)
11. [Validation](#validation)
12. [Code Review Checklist](#code-review-checklist)

---

## Core Principles

Các nguyên tắc cơ bản này áp dụng cho mọi class, method, và module trong aptis-api. Chúng định hình cách tư duy về thiết kế, sao chép code, và xử lý lỗi.

### Nguyên tắc chung (FR-00) — 6 Quy tắc lõi

| Nguyên tắc | Định nghĩa | Ví dụ trong Aptis |
|---|---|---|
| **DRY** (Don't Repeat Yourself) | Code xuất hiện ở 3 nơi trở lên → bắt buộc extract thành function/utility. Không "để sau refactor". | `calculatePassingScore()` xuất hiện trong StudentService, ExamReportService, AnalyticsService → extract thành `ExamConstants.PASSING_SCORE` và `ScoreCalculator.calculatePassingScore(examId)` |
| **KISS** (Keep It Simple, Stupid) | Nếu có 2 cách giải quyết, chọn cái đơn giản hơn — kể cả khi cái phức tạp nghe "cool" hơn. | Đừng dùng reactive stream cho single query đơn giản: dùng `repository.findById()` thay vì `Mono<Exam>` |
| **YAGNI** (You Aren't Gonna Need It) | Không code feature, param, abstraction layer "đề phòng tương lai" khi chưa có yêu cầu cụ thể. | Đừng tạo `BaseExamRepository<T>` generic nếu chỉ cần `ExamRepository` cho exam. Refactor khi có exam attempt repository thứ 2. |
| **Fail Fast** | Validate đầu vào ngay tại boundary (controller/API call), không để lỗi lan sâu vào domain logic. | Kiểm tra `@Valid @RequestBody` ở controller → nếu fail → lỗi được báo tại gateway, không đi vào service layer |
| **Law of Demeter** | Không chain quá 2 level: `a.getB().doSomething()` OK — `a.getB().getC().getD().do()` là code smell. Tạo method trung gian. | `student.getExamAttempt().getExam().getSubject().getName()` → ✗. Tạo `student.getExamSubject()` → ✓ |
| **CQS** (Command-Query Separation) | Một method hoặc trả về giá trị (Query) OR thay đổi state (Command), không làm cả hai. | `getExam(id)` trả về `Exam`. `startExam(examId)` thay đổi state (persisted), trả về `void`. Không có `startExamAndReturnAttemptId()` |

### Clean Code Rules — Tên + Comment + Cấu trúc

```java
// ✗ Violation: Tên rút gọn, không rõ ý nghĩa
int cnt = 0;
void proc() { /* ... */ }
LocalDateTime dt = LocalDateTime.now();

// ✓ Correct: Tên tự giải thích
int attemptCount = 0;
void processSubmittedAnswer() { /* ... */ }
LocalDateTime submissionTime = LocalDateTime.now();
```

```java
// ✗ Violation: Hàm làm nhiều việc
public ExamAttempt loadAndValidateAndStartExam(Long examId, Long studentId) {
    // 30 dòng code: validate, load questions, start timer, persist attempt
}

// ✓ Correct: Tách thành 3 hàm, mỗi hàm 1 việc
public void validateExamAccess(Long examId, Long studentId) { }
public Exam loadExamWithQuestions(Long examId) { }
public ExamAttempt startExamAttempt(Long examId, Long studentId) { }
```

```java
// ✗ Violation: Comment giải thích WHAT (code đã nói rồi)
// Get exam by ID
Exam exam = examRepository.findById(examId).orElseThrow();

// ✓ Correct: Comment giải thích WHY (constraint ẩn, workaround)
// Must load exam before transaction boundary closes to avoid LazyInitializationException
// JPA proxy cannot initialize collections after session ends
Exam exam = examRepository.findById(examId)
    .orElseThrow(() -> new ExamNotFoundException(ExamConstants.EXAM_NOT_FOUND));
```

```java
// ✗ Violation: Hardcoded magic number
if (score >= 50) {
    student.setStatus("PASSED");
}
Thread.sleep(3000);

// ✓ Correct: Tên rõ ý nghĩa
if (score >= ExamConstants.PASSING_SCORE) {
    student.setStatus(StudentConstants.STATUS_PASSED);
}
Thread.sleep(ExamConstants.RETRY_DELAY_MS);
```

### Quyết định checklist: Có vi phạm nguyên tắc không?

```
┌─ Hàm này có "và" trong tên không?
│  ├─ Có → Tách thành 2+ hàm (CQS, Single Responsibility)
│  └─ Không → ✓ OK
│
├─ Có method nào gọi method gọi method (a.b().c().d())?
│  ├─ Có → Tạo method trung gian (Law of Demeter)
│  └─ Không → ✓ OK
│
├─ Có hardcoded string/number trong body?
│  ├─ Có → Extract thành constant (YAGNI + DRY)
│  └─ Không → ✓ OK
│
├─ Có try-catch generic Exception?
│  ├─ Có → Catch cụ thể (Fail Fast)
│  └─ Không → ✓ OK
│
└─ Có feature code "để sau" không?
   ├─ Có → Xóa (YAGNI)
   └─ Không → ✓ OK
```

---

## Naming Conventions

### Classes — `PascalCase`
Tên class phải tự giải thích role của nó trong kiến trúc (Service, Repository, Controller, Exception, DTO, v.v.).

```java
// ✓ Correct
public class ExamService { }
public class ExamAttemptRepository { }
public class SubmitAnswerRequest { }
public class ExamNotFoundException extends RuntimeException { }
public class ExamScheduleHelper { }

// ✗ Violation
public class ES { }  // quá rút gọn
public class ExamAndQuestionService { }  // "và" → tách thành 2 service
public class Helper { }  // generic, không rõ ý
```

### Methods & Fields — `camelCase`
Hàm phải là động từ (hoặc be-verb), field phải là danh từ.

```java
// ✓ Correct
public Exam findByStudentId(Long studentId) { }
private String attemptStatus;
public void submitAnswer(Long answerId) { }
private LocalDateTime createdAt;
public boolean isExamExpired() { }

// ✗ Violation
public Exam find_by_student_id() { }  // snake_case trong Java
public String AS;  // quá rút gọn
void examAndQuestionLogic() { }  // hàm làm 2 việc
private String crdt;  // viết tắt, khó hiểu
```

### Constants — `UPPER_SNAKE_CASE` in `*Constants.java`
Mọi constant string, error code, status label phải đặt trong file `*Constants.java` cùng module. Không hardcode trong method body.

```java
// ExamConstants.java
public class ExamConstants {
    // User-facing messages
    public static final String EXAM_NOT_FOUND = "EXAM_NOT_FOUND";
    public static final String EXAM_ALREADY_STARTED = "EXAM_ALREADY_STARTED";
    
    // Status labels
    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    
    // Business rules
    public static final int PASSING_SCORE = 50;
    public static final int MAX_ATTEMPT_COUNT = 3;
    public static final long RETRY_DELAY_MS = 3000L;
}

// StudentConstants.java
public class StudentConstants {
    public static final String STATUS_PASSED = "PASSED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STUDENT_ALREADY_ENROLLED = "STUDENT_ALREADY_ENROLLED";
}
```

### Packages — `lowercase.dot.separated`
Phải tuân thủ cấu trúc module: `com.aptis.modules.{moduleName}.{layer}`

```
✓ Correct structure:
com.aptis.modules.examdelivery.constant
com.aptis.modules.examdelivery.controller
com.aptis.modules.examdelivery.domain
com.aptis.modules.examdelivery.dto
com.aptis.modules.examdelivery.interfaces
com.aptis.modules.examdelivery.repository
com.aptis.modules.examdelivery.service

✗ Violation:
com.aptis.ExamService  (không module, không layer)
com.aptis.modules.ExamDelivery.service  (PascalCase tên package)
com.aptis.modules.exam_delivery.service  (snake_case)
```

### Naming Anti-patterns — 5 "❌ Don't" vs "✅ Do"

| ❌ Don't | ✅ Do | Giải thích |
|---|---|---|
| `getES()` | `getExamService()` | Tên rút gọn gây nhầm lẫn |
| `ExamAndQuestionService` | Split thành `ExamService` + `QuestionLoaderService` | "Và" → hàm làm 2 việc |
| `findAndDelete()` | Split thành `find()` + `delete()` | CQS: không query+command trong 1 hàm |
| `tmp`, `data`, `val`, `obj` | `examDuration`, `attemptData`, `passingScore`, `studentAttempt` | Tên biến tự giải thích |
| `Helper`, `Utils`, `Processor` | `ExamScheduleHelper`, `DateTimeUtils`, `AnswerProcessor` | Tên generic là code smell |

---

## File Structure & Organization

### Module Layout Diagram
Mỗi module trong aptis-api tuân thủ cấu trúc này:

```
modules/{moduleName}/
├── constant/
│   └── ExamConstants.java          # All message strings, error codes, business rule numbers
├── controller/
│   └── ExamController.java         # REST handlers only, no business logic
├── domain/
│   ├── Exam.java                   # JPA @Entity
│   ├── ExamAttempt.java
│   ├── enums/                      # All enum types owned by this module
│   │   ├── ExamStatus.java
│   │   └── Skill.java
│   ├── event/                      # Domain events (optional)
│   └── exception/
│       ├── ExamNotFoundException.java
│       └── ExamConstraintViolationException.java
├── dto/
│   ├── request/
│   │   ├── CreateExamRequest.java
│   │   └── StartExamRequest.java
│   └── response/
│       ├── ExamResponse.java
│       └── ExamAttemptResponse.java
├── interfaces/
│   └── ExamService.java            # Service interface (SOLID-I)
├── repository/
│   └── ExamRepository.java         # Spring Data JPA
└── service/
    ├── ExamService.java            # implements ExamService interface
    └── ExamScheduleHelper.java     # When ExamService grows >5 public methods
```

### Per-Folder Responsibility & Layer Rules

| Folder | Responsibility | What can import | What can export |
|---|---|---|---|
| **constant/** | Centralize strings, codes, numbers | Nothing | `public static final` constants |
| **controller/** | Parse request, validate at boundary, delegate to service | `dto.request`, `service`, `constant` | `HttpStatus`, `ResponseEntity` |
| **domain/** | Pure domain logic, entities, value objects, custom exceptions | Only other domain classes | `@Entity`, `RuntimeException` subclasses |
| **domain/enums/** | Every `enum` owned by the module — one file per enum | Nothing except `java.*` | `enum` type |
| **dto/** | Request/Response DTO, never expose entity | `domain` (read-only), `constant` | Serializable POJO |
| **interfaces/** | Service contract/interface only | Nothing except generics | `interface` definition |
| **repository/** | Spring Data JPA queries only, no business logic | `domain` | `CrudRepository`, `JpaRepository` |
| **service/** | Business logic, orchestration, transactions | All layers (`dto`, `domain`, `repository`, `constant`) | Service interface |

### Layer Access Rules — Strict
```
controller → service → repository → domain
  ✗ No: controller → repository (bypass service)
  ✗ No: repository → controller (upward reference)
  ✗ No: service → controller (service doesn't know HTTP)
```

### Enum Placement — `domain/enums/`

**Rule:** Mọi `enum` trong module phải nằm trong `domain/enums/`, không rải rác trực tiếp trong `domain/`. Package name là `enums` (số nhiều — `enum` là từ khoá reserved, không dùng làm tên package được). Mục đích: nhìn vào `domain/enums/` là thấy ngay toàn bộ "vocabulary" của module (trạng thái, loại, phân loại) mà không phải lọc giữa entity và enum.

```
✗ Violation:
domain/
├── Exam.java
├── ExamStatus.java      ← enum lẫn với entity, khó tracking khi module lớn
├── Skill.java            ← enum
└── ExamAttempt.java

✓ Correct:
domain/
├── Exam.java
├── ExamAttempt.java
└── enums/
    ├── ExamStatus.java
    └── Skill.java
```

```java
// ✗ Violation: package công bố enum trực tiếp ở domain/
package com.aptis.modules.questionbank.domain;

public enum Skill { READING, LISTENING, WRITING, SPEAKING }

// ✓ Correct: package con enums/
package com.aptis.modules.questionbank.domain.enums;

public enum Skill { READING, LISTENING, WRITING, SPEAKING }
```

Entity dùng enum từ subfolder này qua import bình thường — không có ngoại lệ vì cùng module:

```java
// Question.java (domain/Question.java)
import com.aptis.modules.questionbank.domain.enums.Skill;
import com.aptis.modules.questionbank.domain.enums.QuestionType;
```

**Áp dụng:** New code — bắt buộc từ đầu. Existing code — refactor theo Boy Scout Rule khi động vào file đó (không cần đi dọn hết module cùng lúc, trừ khi đang làm 1 thay đổi lớn như thêm/sửa nhiều enum liên quan — khi đó dọn cả module 1 lần để tránh nửa module theo convention cũ, nửa theo convention mới).

### File Size Limit — Max 300 lines
Nếu file vượt 300 dòng → tách thành file con (`*Helper`, `*Processor`). Kiểm tra: `wc -l ClassName.java`

```java
// ✗ Violation: ExamService.java có 450 dòng
public class ExamService implements ExamService {
    public Exam startExam() { }                    // 40 dòng
    public void submitAnswer() { }                // 50 dòng
    public ExamReport generateReport() { }        // 80 dòng
    public void autoCompleteExpiredExams() { }    // 60 dòng
    public void notifyStudentResults() { }        // 55 dòng
    public void updateExamSchedule() { }          // 45 dòng
    // Vượt 300 → phạm luật
}

// ✓ Correct: Split thành 3 file
// ExamService.java — 120 dòng
public class ExamService implements ExamService {
    private final ExamReportHelper reportHelper;
    private final ExamScheduleHelper scheduleHelper;
    
    public Exam startExam() { }
    public void submitAnswer() { }
    public ExamReport generateReport() {
        return reportHelper.generate(...);  // Delegate
    }
}

// ExamReportHelper.java — 100 dòng
public class ExamReportHelper {
    public ExamReport generate() { }
    public void notifyStudentResults() { }
}

// ExamScheduleHelper.java — 80 dòng
public class ExamScheduleHelper {
    public void autoCompleteExpiredExams() { }
    public void updateExamSchedule() { }
}
```

### When to Split into Helper
**Rule:** Service class có >5 public methods → tách thành Helper + lưu logic vào Helper.

```java
// ✗ Before: 7 public methods
public class ExamService {
    public Exam startExam() { }
    public void submitAnswer() { }
    public ExamReport generateReport() { }
    public void autoCompleteExpiredExams() { }
    public void notifyStudentResults() { }
    public void updateExamSchedule() { }
    public List<Exam> listUpcomingExams() { }
}

// ✓ After: ExamService = 4 public methods, ExamScheduleHelper = 3 public methods
public class ExamService {
    private final ExamScheduleHelper scheduleHelper;
    
    public Exam startExam() { }
    public void submitAnswer() { }
    public ExamReport generateReport() { }
    public List<Exam> listUpcomingExams() { }
}

public class ExamScheduleHelper {
    public void autoCompleteExpiredExams() { }
    public void notifyStudentResults() { }
    public void updateExamSchedule() { }
}
```

---

## SOLID Principles in Spring Boot

### S — Single Responsibility (Một trách nhiệm)

**Rule:** Controller chỉ delegate (request → response). Service xử lý business logic nhưng không quá 5 public methods.

```java
// ✗ Violation: ExamService với 8 public methods (quá trách nhiệm)
public class ExamService {
    public Exam startExamAndValidateStudentAndLoadQuestionsAndScheduleCompletion(Long examId, Long studentId) {
        // 150 dòng: validation, load, schedule
    }
    // + 7 hàm khác
}

// ✓ Correct: Tách thành 3 service, mỗi class <5 methods
public class ExamService {
    private final ExamRepository examRepository;
    
    public Exam startExam(Long examId, Long studentId) {
        Exam exam = examRepository.findById(examId)
            .orElseThrow(() -> new ExamNotFoundException(ExamConstants.EXAM_NOT_FOUND));
        exam.setStatus(ExamConstants.STATUS_IN_PROGRESS);
        return examRepository.save(exam);
    }
    
    public ExamReport generateReport(Long attemptId) { }
    public List<Exam> listUpcomingExams() { }
}

public class QuestionLoaderService {
    public List<Question> loadExamQuestions(Long examId) { }
}

public class ExamScheduleService {
    public void scheduleExamCompletion(Long attemptId) { }
}
```

```java
// ✗ Violation: Controller xử lý business logic
@RestController
@RequestMapping("/exams")
public class ExamController {
    @PostMapping("/{id}/start")
    public ResponseEntity<ExamResponse> startExam(@PathVariable Long id) {
        // 50 dòng: validate exam, check student status, load questions, 
        // update attempt, schedule completion notification
    }
}

// ✓ Correct: Controller chỉ delegate
@RestController
@RequestMapping("/exams")
public class ExamController {
    private final ExamService examService;
    
    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<ExamResponse>> startExam(@PathVariable Long id) {
        Exam exam = examService.startExam(id);
        return ResponseEntity.ok(ApiResponse.success(toResponse(exam)));
    }
}
```

### O — Open/Closed (Mở rộng, Đóng lại với sửa đổi)

**Rule:** Dùng interface + dependency injection. Không `instanceof` trong service logic.

```java
// ✗ Violation: Hardcoding concrete class, khó mở rộng
@Service
public class ExamService {
    private ExamRepositoryImpl repository = new ExamRepositoryImpl();
    
    public Exam getExam(Long id) {
        if (repository instanceof ExamRepositoryImpl) {
            // Phụ thuộc vào cụ thể → khó thay đổi implementation
        }
        return repository.findById(id);
    }
}

// ✓ Correct: Interface + injection
@Service
public class ExamService {
    private final ExamRepository repository;
    
    @Autowired
    public ExamService(ExamRepository repository) {
        this.repository = repository;
    }
    
    public Exam getExam(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ExamNotFoundException(ExamConstants.EXAM_NOT_FOUND));
    }
}

public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findByStatusAndTenantId(String status, Long tenantId);
}
```

```java
// ✗ Violation: Notification hardcoding, khó thêm channel mới
public class ExamService {
    public void notifyExamStart(Long attemptId) {
        sendEmailNotification(attemptId);  // hardcode email
        // Nếu muốn add SMS → phải sửa service
    }
}

// ✓ Correct: Strategy pattern với interface
public interface NotificationStrategy {
    void notify(String message);
}

@Service
public class ExamService {
    private final List<NotificationStrategy> strategies;
    
    public void notifyExamStart(Long attemptId) {
        String message = "Your exam has started";
        strategies.forEach(s -> s.notify(message));  // Mở rộng: add EmailNotification, SmsNotification
    }
}
```

### L — Liskov Substitution (Subclass không làm hỏng contract)

**Rule:** Exception hierarchy phải consistent. Custom exceptions extend `RuntimeException` đúng cách.

```java
// ✗ Violation: Exception hierarchy không consistent
public class ExamNotFoundException extends Exception { }  // checked
public class StudentNotEnrolledException extends RuntimeException { }  // unchecked
// → Caller phải catch 2 loại khác nhau, khó quản lý

// ✓ Correct: Tất cả extend RuntimeException
public class ExamNotFoundException extends RuntimeException {
    public ExamNotFoundException(String message) {
        super(message);
    }
}

public class StudentNotEnrolledException extends RuntimeException {
    public StudentNotEnrolledException(String message) {
        super(message);
    }
}

@Service
public class ExamService {
    public Exam startExam(Long examId, Long studentId) {
        Exam exam = examRepository.findById(examId)
            .orElseThrow(() -> new ExamNotFoundException(ExamConstants.EXAM_NOT_FOUND));
        
        if (!studentService.isEnrolled(studentId, examId)) {
            throw new StudentNotEnrolledException(StudentConstants.NOT_ENROLLED);
        }
        
        return exam;
    }
}
```

### I — Interface Segregation (Interface focused)

**Rule:** Interface chỉ khai báo method thuộc về 1 responsibility, không "tất cả trong 1".

```java
// ✗ Violation: Interface quá lớn (15 methods, 3 responsibilities khác nhau)
public interface ExamOperations {
    // Query operations
    Exam findById(Long id);
    List<Exam> findByStatus(String status);
    
    // Command operations
    Exam save(Exam exam);
    void delete(Long id);
    
    // Report operations
    ExamReport generateReport(Long examId);
    List<String> getStudentAnswers(Long attemptId);
    // ... 9 methods khác
}

// ✓ Correct: Tách thành 3 interface, mỗi cái 1 responsibility
public interface ExamQueries {
    Exam findById(Long id);
    List<Exam> findByStatus(String status);
}

public interface ExamCommands {
    Exam save(Exam exam);
    void delete(Long id);
}

public interface ExamReports {
    ExamReport generateReport(Long examId);
    List<String> getStudentAnswers(Long attemptId);
}

// Service implement interface cần thiết
@Service
public class ExamQueryService implements ExamQueries {
    @Override
    public Exam findById(Long id) { }
    
    @Override
    public List<Exam> findByStatus(String status) { }
}
```

### D — Dependency Inversion (Phụ thuộc vào abstraction)

**Rule:** Service luôn inject qua interface, không inject concrete class.

```java
// ✗ Violation: Inject concrete class, phụ thuộc cụ thể
@Service
public class ExamService {
    @Autowired
    private ExamRepositoryImpl repository;  // concrete!
    
    @Autowired
    private StudentServiceImpl studentService;  // concrete!
}

// ✓ Correct: Inject via interface
@Service
public class ExamService {
    private final ExamRepository repository;
    private final StudentService studentService;
    
    @Autowired
    public ExamService(ExamRepository repository, StudentService studentService) {
        this.repository = repository;
        this.studentService = studentService;
    }
}
```

---

## Constants Management

**Golden Rule:** Mọi user-facing message, error code, status label, config value phải nằm trong `*Constants.java` cùng module. KHÔNG hardcode string trong service/controller body.

### Structure: One Constants File per Module

```java
// ✓ ExamConstants.java
public class ExamConstants {
    // Error messages (user-facing)
    public static final String EXAM_NOT_FOUND = "EXAM_NOT_FOUND";
    public static final String EXAM_ALREADY_STARTED = "EXAM_ALREADY_STARTED";
    public static final String EXAM_TIME_EXPIRED = "EXAM_TIME_EXPIRED";
    
    // Status labels
    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    
    // Business rules (numbers with meaning)
    public static final int PASSING_SCORE = 50;
    public static final int MAX_ATTEMPT_COUNT = 3;
    public static final long EXAM_TIME_LIMIT_MINUTES = 60L;
    public static final long RETRY_DELAY_MS = 3000L;
}

// ✓ StudentConstants.java
public class StudentConstants {
    public static final String STATUS_PASSED = "PASSED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STUDENT_ALREADY_ENROLLED = "STUDENT_ALREADY_ENROLLED";
    public static final String STUDENT_NOT_ENROLLED = "STUDENT_NOT_ENROLLED";
}
```

### Violation Detection & Fixes

| ❌ Hardcoded String | ✓ Extracted Constant | Giải thích |
|---|---|---|
| `exam.setStatus("IN_PROGRESS")` | `exam.setStatus(ExamConstants.STATUS_IN_PROGRESS)` | Magic string → constant |
| `throw new ExamNotFoundException("Exam not found")` | `throw new ExamNotFoundException(ExamConstants.EXAM_NOT_FOUND)` | Error message → constant |
| `if (score >= 50)` | `if (score >= ExamConstants.PASSING_SCORE)` | Magic number → constant |
| `Thread.sleep(3000)` | `Thread.sleep(ExamConstants.RETRY_DELAY_MS)` | Duration → constant |

```java
// ✗ Violation: Hardcoded strings scattered in service
@Service
public class ExamService {
    public Exam startExam(Long examId) {
        Exam exam = examRepository.findById(examId)
            .orElseThrow(() -> new ExamNotFoundException("Exam not found"));
        
        exam.setStatus("IN_PROGRESS");
        exam.setStartTime(LocalDateTime.now());
        
        return examRepository.save(exam);
    }
    
    public void completeExam(Long attemptId) {
        ExamAttempt attempt = repository.findById(attemptId)
            .orElseThrow(() -> new ExamNotFoundException("Exam attempt not found"));
        
        attempt.setStatus("COMPLETED");
        attempt.setEndTime(LocalDateTime.now());
        
        repository.save(attempt);
    }
    
    public boolean isPassingScore(int score) {
        return score >= 50;  // Magic number
    }
}

// ✓ Correct: All constants extracted, centralized
@Service
public class ExamService {
    private final ExamRepository examRepository;
    
    @Autowired
    public ExamService(ExamRepository examRepository) {
        this.examRepository = examRepository;
    }
    
    public Exam startExam(Long examId) {
        Exam exam = examRepository.findById(examId)
            .orElseThrow(() -> new ExamNotFoundException(ExamConstants.EXAM_NOT_FOUND));
        
        exam.setStatus(ExamConstants.STATUS_IN_PROGRESS);
        exam.setStartTime(LocalDateTime.now());
        
        return examRepository.save(exam);
    }
    
    public void completeExam(Long attemptId) {
        ExamAttempt attempt = examRepository.findById(attemptId)
            .orElseThrow(() -> new ExamNotFoundException(ExamConstants.EXAM_NOT_FOUND));
        
        attempt.setStatus(ExamConstants.STATUS_COMPLETED);
        attempt.setEndTime(LocalDateTime.now());
        
        examRepository.save(attempt);
    }
    
    public boolean isPassingScore(int score) {
        return score >= ExamConstants.PASSING_SCORE;
    }
}
```

---

## Exception Handling

### Domain Exception Hierarchy
Custom exceptions trong Aptis phải extend `RuntimeException` (unchecked). Tạo exception cho từng domain rule.

```java
// ✓ Domain Exception Hierarchy
public abstract class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}

public class ExamNotFoundException extends DomainException {
    public ExamNotFoundException(String message) {
        super(message);
    }
}

public class StudentNotEnrolledException extends DomainException {
    public StudentNotEnrolledException(String message) {
        super(message);
    }
}

public class ExamAlreadyStartedException extends DomainException {
    public ExamAlreadyStartedException(String message) {
        super(message);
    }
}

public class InvalidAnswerException extends DomainException {
    public InvalidAnswerException(String message) {
        super(message);
    }
}
```

### @ControllerAdvice Global Handler
Tất cả exception handling đặt ở 1 chỗ, không try-catch ở controller.

```java
// ✓ Global Exception Handler
@ControllerAdvice
@RestController
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ExamNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleExamNotFound(ExamNotFoundException ex) {
        ApiResponse<?> response = ApiResponse.error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    @ExceptionHandler(StudentNotEnrolledException.class)
    public ResponseEntity<ApiResponse<?>> handleNotEnrolled(StudentNotEnrolledException ex) {
        ApiResponse<?> response = ApiResponse.error(ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationError(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldError().getDefaultMessage();
        ApiResponse<?> response = ApiResponse.error(message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleDataIntegrity(DataIntegrityViolationException ex) {
        ApiResponse<?> response = ApiResponse.error("Constraint violation: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGenericException(Exception ex) {
        ApiResponse<?> response = ApiResponse.error("Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
```

```java
// ✗ Violation: try-catch ở controller, code rẻm rách
@RestController
@RequestMapping("/exams")
public class ExamController {
    @PostMapping("/{id}/start")
    public ResponseEntity<?> startExam(@PathVariable Long id) {
        try {
            Exam exam = examService.startExam(id);
            return ResponseEntity.ok(exam);
        } catch (ExamNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (StudentNotEnrolledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Unknown error"));
        }
    }
}

// ✓ Correct: Clean controller, exception handling centralized
@RestController
@RequestMapping("/exams")
public class ExamController {
    private final ExamService examService;
    
    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<ExamResponse>> startExam(@PathVariable Long id) {
        Exam exam = examService.startExam(id);
        return ResponseEntity.ok(ApiResponse.success(toResponse(exam)));
        // Exception thrown → @ControllerAdvice xử lý tự động
    }
}
```

---

## API Response Wrapper

**Rule:** Mọi endpoint phải trả về `ApiResponse<T>` wrapper để consistent format.

### Standard Response Class Definition

```java
// ✓ ApiResponse wrapper
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .message(null)
            .build();
    }
    
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
            .success(false)
            .data(null)
            .message(message)
            .build();
    }
}
```

### HTTP Status Mapping

| Scenario | HTTP Status | Success | Message | Example |
|---|---|---|---|---|
| Normal success | `200 OK` | `true` | `null` | `{ "success": true, "data": { "id": 1, "name": "Math Exam" }, "message": null }` |
| Validation error | `400 Bad Request` | `false` | Error details | `{ "success": false, "data": null, "message": "Name is required" }` |
| Not found | `404 Not Found` | `false` | `EXAM_NOT_FOUND` | `{ "success": false, "data": null, "message": "EXAM_NOT_FOUND" }` |
| Server error | `500 Internal Server Error` | `false` | Generic message | `{ "success": false, "data": null, "message": "Internal server error" }` |

### Example Response Payloads

```json
// Success: GET /exams/1
{
  "success": true,
  "data": {
    "id": 1,
    "title": "Math Entrance",
    "duration": 60,
    "passingScore": 50,
    "status": "SCHEDULED"
  },
  "message": null
}

// Success: POST /exams/1/start (returns empty data)
{
  "success": true,
  "data": null,
  "message": null
}

// Failure: Exam not found
{
  "success": false,
  "data": null,
  "message": "EXAM_NOT_FOUND"
}

// Failure: Validation error
{
  "success": false,
  "data": null,
  "message": "Title is required"
}
```

---

## Transaction & Performance

### @Transactional Rules

**Rule 1:** `@Transactional` chỉ đặt ở **Service layer**, không ở Controller, không ở Repository.
**Rule 2:** Để `readOnly = true` cho mọi method chỉ đọc dữ liệu (query).

```java
// ✗ Violation: @Transactional ở Repository (không cần)
@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {
    @Transactional  // ✗ Không cần, JpaRepository tự handle
    Exam findById(Long id);
}

// ✗ Violation: @Transactional ở Controller
@RestController
@RequestMapping("/exams")
public class ExamController {
    @Transactional  // ✗ Không cần, controller không phải business layer
    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<ExamResponse>> startExam(@PathVariable Long id) { }
}

// ✓ Correct: @Transactional ở Service
@Service
public class ExamService {
    
    // Write operation: không cần readOnly
    @Transactional
    public Exam startExam(Long examId, Long studentId) {
        Exam exam = examRepository.findById(examId)
            .orElseThrow(() -> new ExamNotFoundException(ExamConstants.EXAM_NOT_FOUND));
        
        exam.setStatus(ExamConstants.STATUS_IN_PROGRESS);
        exam.setStartTime(LocalDateTime.now());
        
        return examRepository.save(exam);
    }
    
    // Read operation: readOnly = true (tối ưu lock và connection pool)
    @Transactional(readOnly = true)
    public Exam getExam(Long examId) {
        return examRepository.findById(examId)
            .orElseThrow(() -> new ExamNotFoundException(ExamConstants.EXAM_NOT_FOUND));
    }
    
    // Read multiple: readOnly = true
    @Transactional(readOnly = true)
    public List<Exam> listUpcomingExams() {
        return examRepository.findByStatus(ExamConstants.STATUS_SCHEDULED);
    }
}
```

### N+1 Query Detection & Prevention

**Detect:** Bật `spring.jpa.show-sql=true` trong `application-dev.yaml`. Kiểm tra số query — nếu `SELECT` statement × số objects là N+1, đó là problem.

```yaml
# application-dev.yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

```
# Console log: Nếu query 1 exam và 10 questions
Hibernate: SELECT * FROM exam WHERE id = 1;                    -- 1 query
Hibernate: SELECT * FROM question WHERE exam_id = 1;           -- 1 query
Hibernate: SELECT * FROM question_option WHERE question_id = ?; -- 10 queries
Total: 12 queries for 1 exam (N+1 problem!)
```

**Fix 1: @EntityGraph** — Load related entities eager

```java
// ✗ Violation: Lazy loading → N+1 queries
@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {
    Exam findById(Long id);  // Exam lazy-loads questions
}

@Service
public class ExamService {
    @Transactional(readOnly = true)
    public ExamResponse getExam(Long examId) {
        Exam exam = examRepository.findById(examId);  // 1 query
        return toResponse(exam);  // Accessing exam.getQuestions() → N queries
    }
}

// ✓ Fix 1: @EntityGraph
@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {
    @EntityGraph(attributePaths = {"questions"})
    Exam findById(Long id);  // 1 query with JOIN FETCH
}

@Service
public class ExamService {
    @Transactional(readOnly = true)
    public ExamResponse getExam(Long examId) {
        Exam exam = examRepository.findById(examId);  // 1 query with questions eagerly loaded
        return toResponse(exam);  // No additional queries
    }
}
```

**Fix 2: JOIN FETCH** — Custom JPQL query

```java
// ✓ Fix 2: JPQL JOIN FETCH
@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {
    @Query("SELECT e FROM Exam e LEFT JOIN FETCH e.questions WHERE e.id = :id")
    Exam findByIdWithQuestions(@Param("id") Long id);
}

@Service
public class ExamService {
    @Transactional(readOnly = true)
    public ExamResponse getExam(Long examId) {
        Exam exam = examRepository.findByIdWithQuestions(examId);  // 1 query
        return toResponse(exam);
    }
}
```

### Lombok Gotcha: @Data on @Entity

**Rule:** Không dùng `@Data` trên `@Entity`. Nó generate `hashCode()` + `equals()` dựa trên lazy proxy, gây vòng lặp vô hạn.

```java
// ✗ Violation: @Data on @Entity
@Entity
@Table(name = "exam")
@Data  // ← KHÔNG được phép
public class Exam {
    @Id
    private Long id;
    
    @OneToMany(mappedBy = "exam", fetch = FetchType.LAZY)
    private Set<Question> questions;  // Lazy proxy
    
    // @Data generates hashCode() using questions field
    // → Accessing questions trigger lazy load
    // → Lazy proxy tries to call hashCode()
    // → Infinite loop!
}

// ✓ Correct: Use @Getter, @Setter, @Builder separately
@Entity
@Table(name = "exam")
@Getter
@Setter
@Builder
@RequiredArgsConstructor
@EqualsAndHashCode(of = "id")  // Only use ID for equals/hashCode
public class Exam {
    @Id
    private Long id;
    
    @OneToMany(mappedBy = "exam", fetch = FetchType.LAZY)
    private Set<Question> questions;
}
```

### Concurrency & Race Conditions

**Rule 1:** DB constraints (unique, foreign key) là defense line đầu tiên.
**Rule 2:** Optimistic locking (`@Version`) cho concurrent updates tới entity.
**Rule 3:** Không rely vào application-level check-then-act mà không có DB constraint.

```java
// ✗ Violation: Check-then-act race condition
@Service
public class ExamService {
    public ExamAttempt createExamAttempt(Long studentId, Long examId) {
        // Check: thử tìm attempt cũ
        if (!examAttemptRepository.existsByStudentIdAndExamId(studentId, examId)) {
            // Act: nếu không có → tạo mới
            ExamAttempt attempt = new ExamAttempt();
            attempt.setStudentId(studentId);
            attempt.setExamId(examId);
            return examAttemptRepository.save(attempt);
        }
        throw new ExamAlreadyStartedException(...);
    }
}

// Problem: Nếu 2 thread gọi cùng lúc → cả 2 qua check → cả 2 insert → duplicate!

// ✓ Correct: Use DB constraint + catch exception
@Entity
@Table(name = "exam_attempt", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"student_id", "exam_id"})  // DB constraint
})
public class ExamAttempt {
    @Id
    private Long id;
    private Long studentId;
    private Long examId;
}

@Service
public class ExamService {
    public ExamAttempt createExamAttempt(Long studentId, Long examId) {
        try {
            ExamAttempt attempt = new ExamAttempt();
            attempt.setStudentId(studentId);
            attempt.setExamId(examId);
            return examAttemptRepository.save(attempt);
        } catch (DataIntegrityViolationException e) {
            // DB reject duplicate → race condition safe
            throw new ExamAlreadyStartedException(ExamConstants.EXAM_ALREADY_STARTED);
        }
    }
}
```

**Optimistic Locking with @Version**

```java
// ✓ Optimistic locking for concurrent updates
@Entity
@Table(name = "exam")
@Getter
@Setter
@Builder
public class Exam {
    @Id
    private Long id;
    
    private String title;
    private int passingScore;
    
    @Version  // ← Optimistic locking: increment on every update
    private Long version;
}

@Service
public class ExamService {
    @Transactional
    public Exam updateExam(Long examId, UpdateExamRequest request) {
        Exam exam = examRepository.findById(examId)
            .orElseThrow(() -> new ExamNotFoundException(ExamConstants.EXAM_NOT_FOUND));
        
        exam.setTitle(request.getTitle());
        exam.setPassingScore(request.getPassingScore());
        
        try {
            return examRepository.save(exam);  // version++ on success
        } catch (OptimisticLockingFailureException e) {
            // Another thread modified exam concurrently
            throw new ExamConcurrentModificationException("Exam was modified concurrently");
        }
    }
}
```

---

## Secrets Management

**Golden Rule:** API keys, database passwords, OAuth tokens, private keys — PHẢI dùng environment variables hoặc Spring Cloud Config. KHÔNG bao giờ trong source code, ngay cả `*Constants.java`.

### Violation Examples

```java
// ✗ VIOLATION #1: Secret in Constants.java
public class DatabaseConstants {
    public static final String DB_PASSWORD = "abc123xyz";  // ← Secret exposed!
    public static final String DB_URL = "jdbc:mysql://prod-db:3306/aptis";  // ← Secret!
}

// ✗ VIOLATION #2: Secret in application.properties (hardcoded)
# application.properties
spring.datasource.password=abc123xyz  # ← Committed to Git!
api.key.stripe=sk_live_1234567890  # ← Credentials!

// ✗ VIOLATION #3: Secret in class field (even with @Value)
@Configuration
public class ExternalApiConfig {
    @Value("stripe_secret_key_hardcoded_here")
    private String stripeKey;  // ← Hardcoded string is still exposed!
}

// ✗ VIOLATION #4: OAuth credentials in constants
public class OAuthConstants {
    public static final String CLIENT_ID = "google_client_id_xyz";
    public static final String CLIENT_SECRET = "google_client_secret_abc";  // ← Secret!
}
```

### Correct Approach: Environment Variables + Spring

```java
// ✓ Correct: Use @Value to inject from environment
@Configuration
public class DatabaseConfig {
    @Value("${spring.datasource.password}")
    private String dbPassword;
    
    @Value("${app.stripe.secret-key}")
    private String stripeKey;
    
    @Bean
    public DataSource dataSource() {
        // dbPassword comes from env var, not hardcoded
        return DriverManagerDataSource.builder()
            .password(dbPassword)
            .build();
    }
}

// ✓ Correct: application.properties references env var
# application.properties (committed to Git)
spring.datasource.password=${DB_PASSWORD}
app.stripe.secret-key=${STRIPE_SECRET_KEY}
app.oauth.google.client-secret=${GOOGLE_CLIENT_SECRET}

// ✓ Correct: Set environment variables in deployment
# .env (Git-ignored)
DB_PASSWORD=abc123xyz
STRIPE_SECRET_KEY=sk_live_1234567890
GOOGLE_CLIENT_SECRET=google_secret_abc
```

### Distinction: Constants vs Secrets

| Data | Storage | Type | Example |
|---|---|---|---|
| **Constants** | `*Constants.java` | Non-sensitive config | `PASSING_SCORE = 50`, `STATUS_IN_PROGRESS`, `EXAM_NOT_FOUND` |
| **Secrets** | Environment variable | Sensitive credentials | DB password, API key, OAuth token, private key |
| **Non-sensitive config** | `application.properties` | App settings | `server.port`, `logging.level`, `spring.jpa.show-sql` |

---

## Multi-Tenant Isolation

**Nguyên tắc áp dụng:**
- **New code:** Apply immediately — bắt buộc từ first commit.
- **Existing code:** Refactor gradually — prioritize khi modify file, tuân theo Boy Scout Rule.

### Standard Layer Assignment

```
┌─ Controller ─┐
│ Extract      │ Extract tenantId from security context or header
│ tenantId     │ @AuthenticationPrincipal, SecurityContextHolder, @RequestHeader
└──────┬───────┘
       │ tenantId
       ↓
┌─ Service ────┐
│ Receive      │ Method signature includes tenantId parameter
│ tenantId     │ No static/thread-local access to tenant
│ param        │ Validate: current user belongs to tenantId
└──────┬───────┘
       │ tenantId
       ↓
┌─ Repository ─┐
│ Filter       │ Every query includes WHERE tenantId = ?
│ by tenantId  │ Never query without tenant scope
└──────────────┘
```

### Implementation Example

```java
// ✗ Violation: No tenant isolation
@RestController
@RequestMapping("/exams")
public class ExamController {
    private final ExamService examService;
    
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExamResponse>>> listExams() {
        // Gets ALL exams across all tenants!
        List<Exam> exams = examService.getExams();
        return ResponseEntity.ok(ApiResponse.success(toResponses(exams)));
    }
}

@Service
public class ExamService {
    public List<Exam> getExams() {
        return examRepository.findAll();  // No tenant filter
    }
}

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findAll();  // Returns all exams, all tenants
}

// ✓ Correct: Tenant-aware at all layers
@RestController
@RequestMapping("/exams")
public class ExamController {
    private final ExamService examService;
    
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExamResponse>>> listExams(
        @AuthenticationPrincipal CustomUserDetails user) {  // Get tenantId from security context
        
        List<Exam> exams = examService.getExams(user.getTenantId());  // Pass tenantId
        return ResponseEntity.ok(ApiResponse.success(toResponses(exams)));
    }
}

@Service
public class ExamService {
    private final ExamRepository examRepository;
    
    @Transactional(readOnly = true)
    public List<Exam> getExams(Long tenantId) {  // tenantId as parameter
        return examRepository.findByTenantId(tenantId);  // Filter by tenant
    }
    
    @Transactional
    public Exam startExam(Long examId, Long studentId, Long tenantId) {
        Exam exam = examRepository.findByIdAndTenantId(examId, tenantId)  // Tenant-scoped query
            .orElseThrow(() -> new ExamNotFoundException(ExamConstants.EXAM_NOT_FOUND));
        
        // Validate student belongs to this tenant
        studentService.validateStudentInTenant(studentId, tenantId);
        
        exam.setStatus(ExamConstants.STATUS_IN_PROGRESS);
        return examRepository.save(exam);
    }
}

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {
    @Query("SELECT e FROM Exam e WHERE e.id = :id AND e.tenantId = :tenantId")
    Optional<Exam> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);
    
    List<Exam> findByTenantId(Long tenantId);  // Tenant-filtered
}
```

### Boy Scout Rule Application

Khi modify existing service không có tenant isolation, update đó:

```java
// Before: No tenant isolation
@Service
public class LegacyExamService {
    public List<Exam> listExams() {
        return examRepository.findAll();  // Gets ALL exams
    }
}

// After: Add tenantId parameter (Boy Scout Rule)
@Service
public class LegacyExamService {
    public List<Exam> listExams(Long tenantId) {  // tenantId added
        return examRepository.findByTenantId(tenantId);  // Now filtered
    }
}

// Controller updated to pass tenantId
@RestController
public class ExamController {
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExamResponse>>> listExams(
        @AuthenticationPrincipal CustomUserDetails user) {
        
        List<Exam> exams = examService.listExams(user.getTenantId());  // Pass tenantId
        return ResponseEntity.ok(ApiResponse.success(toResponses(exams)));
    }
}
```

---

## Validation

**Rule:** Validate tại boundary (controller parameter). Không validate thủ công trong Service.

### Bean Validation Annotations on DTO

```java
// ✓ DTO with Bean Validation annotations
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateExamRequest {
    @NotBlank(message = "Title is required")
    private String title;
    
    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    @Max(value = 240, message = "Duration cannot exceed 240 minutes")
    private Integer duration;
    
    @NotNull(message = "Passing score is required")
    @Min(value = 0)
    @Max(value = 100)
    private Integer passingScore;
    
    @Size(min = 1, max = 100, message = "Subject must be between 1 and 100 characters")
    private String subject;
}
```

```java
// ✓ Controller uses @Valid
@RestController
@RequestMapping("/exams")
public class ExamController {
    private final ExamService examService;
    
    @PostMapping
    public ResponseEntity<ApiResponse<ExamResponse>> createExam(
        @Valid @RequestBody CreateExamRequest request,  // ← @Valid triggers validation
        @AuthenticationPrincipal CustomUserDetails user) {
        
        // If request is invalid → @ControllerAdvice catches MethodArgumentNotValidException
        // If valid → proceed to service
        
        Exam exam = examService.createExam(request, user.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(toResponse(exam)));
    }
}
```

```java
// ✗ Violation: Manual validation in Service
@Service
public class ExamService {
    public Exam createExam(CreateExamRequest request, Long tenantId) {
        // Manual null-checking → code smell
        if (request.getTitle() == null || request.getTitle().isEmpty()) {
            throw new ValidationException("Title is required");
        }
        if (request.getDuration() == null || request.getDuration() < 1) {
            throw new ValidationException("Duration must be at least 1");
        }
        if (request.getPassingScore() == null || request.getPassingScore() < 0) {
            throw new ValidationException("Passing score cannot be negative");
        }
        
        // Create exam...
    }
}

// ✓ Correct: Validation moved to DTO, Service assumes valid
@Service
public class ExamService {
    public Exam createExam(CreateExamRequest request, Long tenantId) {
        // request is guaranteed valid here (passed @Valid)
        Exam exam = Exam.builder()
            .title(request.getTitle())
            .duration(request.getDuration())
            .passingScore(request.getPassingScore())
            .tenantId(tenantId)
            .status(ExamConstants.STATUS_SCHEDULED)
            .build();
        
        return examRepository.save(exam);
    }
}
```

---

## Code Review Checklist

Checklist này dùng trong PR review. Tất cả items phải ✓ trước merge.

### 10+ Actionable Items

- [ ] **No hardcoded strings in business logic**
  - Check: Service/Controller body không chứa string literals (ngoại trừ log message)
  - Fix: Extract thành `*Constants.java`
  - Example: `exam.setStatus("IN_PROGRESS")` → `exam.setStatus(ExamConstants.STATUS_IN_PROGRESS)`

- [ ] **File size < 300 lines**
  - Check: `wc -l ClassName.java` — tất cả class phải ≤ 300 dòng (exclude generated code)
  - Fix: Split thành `*Helper`, `*Processor` nếu vượt
  - Tool: Linting rule từ `@SuppressWarnings` không được dùng để bypass

- [ ] **Service class ≤ 5 public methods**
  - Check: Count `public [type]` methods — phải ≤ 5
  - Fix: Extract thành `*Helper` service hoặc split responsibility
  - Example: `ExamService` 7 methods → split thành `ExamService` (4) + `ExamReportHelper` (3)

- [ ] **All API responses use ApiResponse<T> wrapper**
  - Check: Controller method trả về `ResponseEntity<ApiResponse<...>>` hoặc `ResponseEntity<?>`
  - Check: Response JSON có `{ "success": boolean, "data": T, "message": String }`
  - Fix: Wrap response thành `ApiResponse.success(data)` hoặc `ApiResponse.error(message)`

- [ ] **@Transactional only at Service layer**
  - Check: Không có `@Transactional` trên Controller, Repository
  - Check: Read-only method có `@Transactional(readOnly = true)`
  - Fix: Move annotation từ Controller → Service; add readOnly = true

- [ ] **Exception handling via @ControllerAdvice**
  - Check: Controller không chứa try-catch (ngoại trừ resource cleanup)
  - Check: Custom exception được catch trong `GlobalExceptionHandler`
  - Fix: Remove try-catch từ controller, add handler ở `@ControllerAdvice`

- [ ] **N+1 detection: Log query count**
  - Check: Dev log với `spring.jpa.show-sql=true` để đếm query
  - Fix: Add `@EntityGraph` hoặc JPQL `JOIN FETCH` nếu N+1 detected
  - Example: "Loaded 1 exam + 50 questions = 51 queries" → use @EntityGraph

- [ ] **@Data not used on @Entity classes**
  - Check: Entity không có `@Data` annotation
  - Fix: Replace `@Data` với `@Getter @Setter @Builder`
  - Reason: @Data gây vòng lặp hashCode/equals với lazy proxy

- [ ] **Constants follow UPPER_SNAKE_CASE in *Constants.java**
  - Check: Mọi string/number constant nằm trong `ExamConstants`, `StudentConstants`, etc.
  - Check: Field name là `UPPER_SNAKE_CASE`
  - Fix: Move hardcoded value thành constant file

- [ ] **All enums live in `domain/enums/`**
  - Check: Không có file `*.java` chứa `public enum` nằm trực tiếp trong `domain/` (phải ở `domain/enums/`)
  - Fix: Move file, đổi package thành `...domain.enums`, cập nhật import ở mọi nơi tham chiếu

- [ ] **DTOs never expose domain objects directly**
  - Check: Response DTO không có `@Entity` field
  - Check: Response DTO không return `Exam` object trực tiếp
  - Fix: Create `ExamResponse` DTO, copy field từ `Exam` → `ExamResponse`

- [ ] **Tenant isolation on new code**
  - Check: New method có `tenantId` parameter
  - Check: Repository query có `AND tenantId = ?` filter
  - Fix: Add tenantId parameter, update repository query

- [ ] **Validation at boundary with @Valid**
  - Check: Controller parameter có `@Valid @RequestBody DTO`
  - Check: DTO field có Bean Validation annotation (`@NotNull`, `@NotBlank`, `@Size`, etc.)
  - Fix: Add `@Valid`, add annotation trên DTO field

---

## Common Mistakes from Aptis Codebase

### Mistake #1: N+1 Query in List Endpoint

```java
// ✗ Before: N+1 query
@Service
public class ExamService {
    @Transactional(readOnly = true)
    public List<ExamResponse> listExams(Long tenantId) {
        List<Exam> exams = examRepository.findByTenantId(tenantId);  // 1 query
        
        return exams.stream()
            .map(exam -> {
                // Accessing exam.getQuestions() inside loop → N queries
                int questionCount = exam.getQuestions().size();
                return toResponse(exam, questionCount);
            })
            .collect(Collectors.toList());  // Total: 1 + N queries
    }
}

// ✓ After: Use @EntityGraph
@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {
    @EntityGraph(attributePaths = {"questions"})  // ← Eager load questions
    List<Exam> findByTenantId(Long tenantId);
}

@Service
public class ExamService {
    @Transactional(readOnly = true)
    public List<ExamResponse> listExams(Long tenantId) {
        List<Exam> exams = examRepository.findByTenantId(tenantId);  // 1 query with JOIN
        
        return exams.stream()
            .map(this::toResponse)  // No additional queries
            .collect(Collectors.toList());  // Total: 1 query
    }
}
```

### Mistake #2: Hardcoded Error String in Service

```java
// ✗ Before: Hardcoded message scattered
@Service
public class ExamService {
    public Exam startExam(Long examId) {
        Optional<Exam> exam = examRepository.findById(examId);
        
        if (!exam.isPresent()) {
            throw new ExamNotFoundException("Exam not found");  // ✗ Hardcoded
        }
        
        if (!exam.get().isActive()) {
            throw new ExamInactiveException("Exam is inactive");  // ✗ Hardcoded
        }
        
        return exam.get();
    }
}

// ✓ After: Constants centralized
// ExamConstants.java
public class ExamConstants {
    public static final String EXAM_NOT_FOUND = "EXAM_NOT_FOUND";
    public static final String EXAM_INACTIVE = "EXAM_INACTIVE";
}

@Service
public class ExamService {
    public Exam startExam(Long examId) {
        Exam exam = examRepository.findById(examId)
            .orElseThrow(() -> new ExamNotFoundException(ExamConstants.EXAM_NOT_FOUND));
        
        if (!exam.isActive()) {
            throw new ExamInactiveException(ExamConstants.EXAM_INACTIVE);
        }
        
        return exam;
    }
}
```

### Mistake #3: Missing Tenant Isolation in Query

```java
// ✗ Before: No tenant filter
@Service
public class ExamService {
    public Exam getExam(Long examId) {
        // Nếu attackerX request exam của tenantY → API returns it!
        return examRepository.findById(examId)
            .orElseThrow(() -> new ExamNotFoundException(ExamConstants.EXAM_NOT_FOUND));
    }
}

// ✓ After: Tenant-scoped query
@Service
public class ExamService {
    public Exam getExam(Long examId, Long tenantId) {
        // examId AND tenantId → secure
        return examRepository.findByIdAndTenantId(examId, tenantId)
            .orElseThrow(() -> new ExamNotFoundException(ExamConstants.EXAM_NOT_FOUND));
    }
}

// Updated in Controller
@RestController
@RequestMapping("/exams")
public class ExamController {
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExamResponse>> getExam(
        @PathVariable Long id,
        @AuthenticationPrincipal CustomUserDetails user) {  // Get tenantId
        
        Exam exam = examService.getExam(id, user.getTenantId());  // Pass tenantId
        return ResponseEntity.ok(ApiResponse.success(toResponse(exam)));
    }
}
```

---

## Summary & Quick Reference

### Nguyên tắc 6 chữ vàng
1. **DRY** — Không copy-paste 3 lần
2. **KISS** — Chọn cách đơn giản
3. **YAGNI** — Không code tương lai
4. **Fail Fast** — Validate ở boundary
5. **Law of Demeter** — Max 2 level chain
6. **CQS** — Query hoặc Command, không cả 2

### File Structure
- `constant/` — String, error code, number constants
- `controller/` — REST handler, delegate to service
- `domain/` — JPA Entity, custom exception
- `dto/` — Request/Response POJO
- `repository/` — Spring Data JPA
- `service/` — Business logic, transaction boundary

### Core Rules
- ✅ Constants ở `*Constants.java` — không hardcode
- ✅ @Transactional ở Service layer, readOnly=true for queries
- ✅ Exception via @ControllerAdvice, không try-catch ở controller
- ✅ API response: `ApiResponse<T>` wrapper
- ✅ Tenant isolation: tenantId parameter từ controller → service → repository
- ✅ No `@Data` on @Entity — use `@Getter @Setter @Builder`
- ✅ File size < 300 lines, service < 5 public methods
- ✅ Secrets ở environment variable, không source code
- ✅ N+1 detection: enable `spring.jpa.show-sql=true`, fix với @EntityGraph

### When in Doubt
1. Does the function do >1 thing? → Split it
2. Is string hardcoded in code? → Move to constant
3. Is there try-catch in controller? → Move to @ControllerAdvice
4. Are you loading N objects and accessing 1-to-many? → Check N+1, add @EntityGraph
5. Is tenantId missing from query? → Add tenant filter

---

**Last Updated:** 2026-06-24  
**Status:** Production Ready  
**Maintainer:** Tech Lead, Aptis Team
