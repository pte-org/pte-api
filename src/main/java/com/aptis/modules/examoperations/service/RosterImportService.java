package com.aptis.modules.examoperations.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Transactional
public class RosterImportService {

    private final CredentialProvisioning credentialProvisioning;
    private final ImportBatchRepository importBatchRepository;
    private final StudentRepository studentRepository;
    private final StudentCredentialPendingRepository credentialPendingRepository;
    private final ExamRepository examRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RosterImportService(
            CredentialProvisioning credentialProvisioning,
            ImportBatchRepository importBatchRepository,
            StudentRepository studentRepository,
            StudentCredentialPendingRepository credentialPendingRepository,
            ExamRepository examRepository) {
        this.credentialProvisioning = credentialProvisioning;
        this.importBatchRepository = importBatchRepository;
        this.studentRepository = studentRepository;
        this.credentialPendingRepository = credentialPendingRepository;
        this.examRepository = examRepository;
    }

    /**
     * Provisions student accounts for each valid row and persists the import report.
     *
     * @param hostId        the host's ID (from JWT principal)
     * @param organizationId the organization ID (from JWT tenant claim)
     * @param filename      original uploaded filename
     * @param validRows     parsed rows that passed validation
     * @param errors        validation errors from parsing phase (FS2-04/05)
     * @return import report with counts and errors
     */
    public RosterImportResponse provisionStudents(
            String hostId,
            Long organizationId,
            String filename,
            List<StudentRowData> validRows,
            List<RowError> errors) {

        ImportBatch batch = ImportBatch.create(hostId, "STUDENT", filename);
        batch = importBatchRepository.save(batch);

        List<RowError> allErrors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
        int successCount = 0;

        for (StudentRowData row : validRows) {
            String username = hostId + "_" + row.studentCode();
            String plaintext = credentialProvisioning.generateRandomCredential();
            String hash = credentialProvisioning.hashPassword(plaintext);

            Student student = Student.create(
                    username,
                    organizationId,
                    hostId,
                    row.fullName(),
                    row.studentCode(),
                    row.className(),
                    row.email(),
                    row.phone(),
                    hash);
            student = studentRepository.save(student);

            StudentCredentialPending pending = StudentCredentialPending.create(
                    student.getId(),
                    batch.getId(),
                    plaintext);
            credentialPendingRepository.save(pending);

            successCount++;
        }

        String errorsJson = serializeErrors(allErrors);
        batch.markCompleted(successCount, allErrors.size(), errorsJson);
        importBatchRepository.save(batch);

        return new RosterImportResponse(
                batch.getId(),
                successCount,
                allErrors.size(),
                allErrors);
    }

    /**
     * Retrieves a stored import report, scoped to the authenticated host.
     */
    @Transactional(readOnly = true)
    public RosterImportResponse getImportReport(String hostId, Long batchId) {
        ImportBatch batch = importBatchRepository.findByIdAndHostId(batchId, hostId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        List<RowError> errors = deserializeErrors(batch.getErrorsJson());
        int validCount = batch.getValidCount() != null ? batch.getValidCount() : 0;
        int errorCount = batch.getErrorCount() != null ? batch.getErrorCount() : 0;

        return new RosterImportResponse(batch.getId(), validCount, errorCount, errors);
    }

    /**
     * Assigns an exam to an import batch. Idempotent — reassign overwrites.
     */
    public AssignExamResponse assignExamToBatch(String hostId, Long batchId, Long examId) {
        ImportBatch batch = importBatchRepository.findByIdAndHostId(batchId, hostId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!Boolean.TRUE.equals(exam.getIsAssignable())) {
            throw new ApiException(ErrorCode.EXAM_NOT_ASSIGNABLE);
        }

        batch.setExamId(examId);
        importBatchRepository.save(batch);

        return new AssignExamResponse(
                batch.getId(),
                exam.getId(),
                exam.getName(),
                batch.getStatus());
    }

    private String serializeErrors(List<RowError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<RowError> deserializeErrors(String errorsJson) {
        if (errorsJson == null || errorsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(errorsJson, new TypeReference<List<RowError>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
