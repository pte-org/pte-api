package com.aptis.modules.examoperations.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.modules.examoperations.domain.Exam;
import com.aptis.modules.examoperations.domain.ImportBatch;
import com.aptis.modules.examoperations.domain.StudentCredentialPending;
import com.aptis.modules.examoperations.dto.AssignExamResponse;
import com.aptis.modules.examoperations.dto.RosterImportResponse;
import com.aptis.modules.examoperations.dto.RowError;
import com.aptis.modules.examoperations.dto.StudentRowData;
import com.aptis.modules.examoperations.repository.ExamRepository;
import com.aptis.modules.examoperations.repository.ImportBatchRepository;
import com.aptis.modules.examoperations.repository.StudentCredentialPendingRepository;
import com.aptis.modules.iam.domain.Student;
import com.aptis.modules.iam.interfaces.CredentialProvisioning;
import com.aptis.modules.iam.repository.StudentRepository;

@ExtendWith(MockitoExtension.class)
class RosterImportServiceTest {

    @Mock
    private CredentialProvisioning credentialProvisioning;
    @Mock
    private ImportBatchRepository importBatchRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private StudentCredentialPendingRepository credentialPendingRepository;
    @Mock
    private ExamRepository examRepository;

    private RosterImportService service;

    private static final String HOST_ID = "1";
    private static final Long ORGANIZATION_ID = 100L;
    private static final String FILENAME = "roster_2026.xlsx";

    @BeforeEach
    void setUp() {
        service = new RosterImportService(
                credentialProvisioning,
                importBatchRepository,
                studentRepository,
                credentialPendingRepository,
                examRepository);
    }

    @Nested
    @DisplayName("provisionStudents")
    class ProvisionStudentsTests {

        @Test
        @DisplayName("3 valid rows → 3 Students + 3 credentials created, report correct")
        void provisionValidRows() {
            List<StudentRowData> rows = List.of(
                    new StudentRowData(1, "Nguyen Van A", "SV001", "12A1", "a@test.com", "0901"),
                    new StudentRowData(2, "Tran Thi B", "SV002", "12A2", "b@test.com", "0902"),
                    new StudentRowData(3, "Le Van C", "SV003", "12A1", "c@test.com", "0903"));

            when(credentialProvisioning.generateRandomCredential()).thenReturn("Pass1234!");
            when(credentialProvisioning.hashPassword("Pass1234!")).thenReturn("$2a$12$hashed");

            ImportBatch savedBatch = ImportBatch.create(HOST_ID, "STUDENT", FILENAME);
            // Simulate ID assignment after save
            when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> {
                ImportBatch batch = invocation.getArgument(0);
                setId(batch, 42L);
                return batch;
            });

            when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> {
                Student student = invocation.getArgument(0);
                setStudentId(student, (long) (rows.indexOf(
                        rows.stream()
                                .filter(r -> student.getStudentCode().equals(r.studentCode()))
                                .findFirst()
                                .orElse(rows.get(0))) + 1));
                return student;
            });

            when(credentialPendingRepository.save(any(StudentCredentialPending.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            RosterImportResponse response = service.provisionStudents(
                    HOST_ID, ORGANIZATION_ID, FILENAME, rows, Collections.emptyList());

            assertThat(response.batchId()).isEqualTo(42L);
            assertThat(response.validCount()).isEqualTo(3);
            assertThat(response.errorCount()).isZero();
            assertThat(response.errors()).isEmpty();

            verify(studentRepository, times(3)).save(any(Student.class));
            verify(credentialPendingRepository, times(3)).save(any(StudentCredentialPending.class));
            verify(credentialProvisioning, times(3)).generateRandomCredential();
            verify(credentialProvisioning, times(3)).hashPassword("Pass1234!");
        }

        @Test
        @DisplayName("valid rows with pre-existing errors → report includes all errors")
        void provisionWithExistingErrors() {
            List<StudentRowData> rows = List.of(
                    new StudentRowData(1, "Nguyen Van A", "SV001", "12A1", "a@test.com", "0901"));
            List<RowError> errors = List.of(
                    new RowError(2, "studentCode", "required"),
                    new RowError(3, "fullName", "required"));

            when(credentialProvisioning.generateRandomCredential()).thenReturn("Pass1234!");
            when(credentialProvisioning.hashPassword(anyString())).thenReturn("$2a$12$hashed");
            when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> {
                ImportBatch batch = invocation.getArgument(0);
                setId(batch, 43L);
                return batch;
            });
            when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> {
                Student student = invocation.getArgument(0);
                setStudentId(student, 10L);
                return student;
            });
            when(credentialPendingRepository.save(any(StudentCredentialPending.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            RosterImportResponse response = service.provisionStudents(
                    HOST_ID, ORGANIZATION_ID, FILENAME, rows, errors);

            assertThat(response.validCount()).isEqualTo(1);
            assertThat(response.errorCount()).isEqualTo(2);
            assertThat(response.errors()).hasSize(2);
        }

        @Test
        @DisplayName("username follows hostId_studentCode pattern")
        void usernamePattern() {
            List<StudentRowData> rows = List.of(
                    new StudentRowData(1, "Nguyen Van A", "SV001", "12A1", "a@test.com", "0901"));

            when(credentialProvisioning.generateRandomCredential()).thenReturn("Pass1234!");
            when(credentialProvisioning.hashPassword(anyString())).thenReturn("$2a$12$hashed");
            when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> {
                ImportBatch batch = invocation.getArgument(0);
                setId(batch, 44L);
                return batch;
            });
            when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> {
                Student student = invocation.getArgument(0);
                setStudentId(student, 10L);
                return student;
            });
            when(credentialPendingRepository.save(any(StudentCredentialPending.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.provisionStudents(HOST_ID, ORGANIZATION_ID, FILENAME, rows, Collections.emptyList());

            ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
            verify(studentRepository).save(captor.capture());
            assertThat(captor.getValue().getUsername()).isEqualTo("1_SV001");
        }
    }

    @Nested
    @DisplayName("getImportReport")
    class GetImportReportTests {

        @Test
        @DisplayName("matching hostId → returns stored report")
        void getReportSuccess() {
            ImportBatch batch = ImportBatch.create(HOST_ID, "STUDENT", FILENAME);
            setId(batch, 42L);
            batch.markCompleted(5, 2, "[{\"row\":3,\"field\":\"email\",\"reason\":\"invalid\"},"
                    + "{\"row\":7,\"field\":\"studentCode\",\"reason\":\"duplicate\"}]");

            when(importBatchRepository.findByIdAndHostId(42L, HOST_ID))
                    .thenReturn(Optional.of(batch));

            RosterImportResponse response = service.getImportReport(HOST_ID, 42L);

            assertThat(response.batchId()).isEqualTo(42L);
            assertThat(response.validCount()).isEqualTo(5);
            assertThat(response.errorCount()).isEqualTo(2);
            assertThat(response.errors()).hasSize(2);
            assertThat(response.errors().get(0).row()).isEqualTo(3);
        }

        @Test
        @DisplayName("wrong hostId → RESOURCE_NOT_FOUND")
        void getReportWrongHost() {
            when(importBatchRepository.findByIdAndHostId(42L, "wrong_host"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getImportReport("wrong_host", 42L))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        }

        @Test
        @DisplayName("batch not found → RESOURCE_NOT_FOUND")
        void getReportNotFound() {
            when(importBatchRepository.findByIdAndHostId(999L, HOST_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getImportReport(HOST_ID, 999L))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("assignExamToBatch")
    class AssignExamTests {

        @Test
        @DisplayName("assign assignable exam → success, examId set on batch")
        void assignSuccess() {
            ImportBatch batch = ImportBatch.create(HOST_ID, "STUDENT", FILENAME);
            setId(batch, 42L);
            batch.markCompleted(5, 0, "[]");

            Exam exam = createExam(10L, "Math Exam", "MATH01", true);

            when(importBatchRepository.findByIdAndHostId(42L, HOST_ID))
                    .thenReturn(Optional.of(batch));
            when(examRepository.findById(10L)).thenReturn(Optional.of(exam));
            when(importBatchRepository.save(any(ImportBatch.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AssignExamResponse response = service.assignExamToBatch(HOST_ID, 42L, 10L);

            assertThat(response.batchId()).isEqualTo(42L);
            assertThat(response.examId()).isEqualTo(10L);
            assertThat(response.examName()).isEqualTo("Math Exam");
            assertThat(batch.getExamId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("assign non-assignable exam → EXAM_NOT_ASSIGNABLE")
        void assignNonAssignable() {
            ImportBatch batch = ImportBatch.create(HOST_ID, "STUDENT", FILENAME);
            setId(batch, 42L);

            Exam exam = createExam(10L, "Empty Exam", "EMPTY01", false);

            when(importBatchRepository.findByIdAndHostId(42L, HOST_ID))
                    .thenReturn(Optional.of(batch));
            when(examRepository.findById(10L)).thenReturn(Optional.of(exam));

            assertThatThrownBy(() -> service.assignExamToBatch(HOST_ID, 42L, 10L))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.EXAM_NOT_ASSIGNABLE));

            verify(importBatchRepository, never()).save(any(ImportBatch.class));
        }

        @Test
        @DisplayName("reassign different exam → idempotent overwrite")
        void reassignIdempotent() {
            ImportBatch batch = ImportBatch.create(HOST_ID, "STUDENT", FILENAME);
            setId(batch, 42L);
            batch.setExamId(10L); // already assigned to exam 10

            Exam newExam = createExam(20L, "Science Exam", "SCI01", true);

            when(importBatchRepository.findByIdAndHostId(42L, HOST_ID))
                    .thenReturn(Optional.of(batch));
            when(examRepository.findById(20L)).thenReturn(Optional.of(newExam));
            when(importBatchRepository.save(any(ImportBatch.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AssignExamResponse response = service.assignExamToBatch(HOST_ID, 42L, 20L);

            assertThat(response.examId()).isEqualTo(20L);
            assertThat(batch.getExamId()).isEqualTo(20L); // overwritten
        }

        @Test
        @DisplayName("batch not found → RESOURCE_NOT_FOUND")
        void assignBatchNotFound() {
            when(importBatchRepository.findByIdAndHostId(999L, HOST_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assignExamToBatch(HOST_ID, 999L, 10L))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        }

        @Test
        @DisplayName("exam not found → RESOURCE_NOT_FOUND")
        void assignExamNotFound() {
            ImportBatch batch = ImportBatch.create(HOST_ID, "STUDENT", FILENAME);
            setId(batch, 42L);

            when(importBatchRepository.findByIdAndHostId(42L, HOST_ID))
                    .thenReturn(Optional.of(batch));
            when(examRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assignExamToBatch(HOST_ID, 42L, 999L))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        }
    }

    // --- Test helpers ---

    private void setId(ImportBatch batch, Long id) {
        try {
            var field = ImportBatch.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(batch, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setStudentId(Student student, Long id) {
        try {
            // Student extends BaseEntity which has the id field
            var field = student.getClass().getSuperclass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(student, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Exam createExam(Long id, String name, String code, boolean assignable) {
        try {
            var constructor = Exam.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Exam exam = constructor.newInstance();
            var idField = Exam.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(exam, id);
            exam.setName(name);
            exam.setCode(code);
            var assignField = Exam.class.getDeclaredField("isAssignable");
            assignField.setAccessible(true);
            assignField.set(exam, assignable);
            exam.setHostId(HOST_ID);
            return exam;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
