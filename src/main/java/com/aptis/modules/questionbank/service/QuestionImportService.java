package com.aptis.modules.questionbank.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.excel.EasyExcel;
import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.questionbank.constant.QuestionBankApiConstants;
import com.aptis.modules.questionbank.domain.enums.Skill;
import com.aptis.modules.questionbank.domain.exception.QuestionImportValidationException;
import com.aptis.modules.questionbank.dto.response.QuestionImportResultResponse;
import com.aptis.modules.questionbank.dto.response.QuestionImportRowError;
import com.aptis.modules.questionbank.util.ByteArrayMultipartFile;
import com.aptis.modules.storage.constant.StorageConstants;
import com.aptis.modules.storage.domain.exception.StorageException;
import com.aptis.modules.storage.dto.response.UploadResponse;
import com.aptis.modules.storage.interfaces.StorageService;

/**
 * Orchestrates bulk import: parse → validate every row up front → upload audio (outside
 * any DB transaction) → hand fully-prepared rows to {@link QuestionImportWriter} for a
 * single atomic write. Scoped to Host tenants only (Vendor uses one-by-one authoring).
 */
@Service
public class QuestionImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestionImportService.class);

    private final QuestionActorResolver actorResolver;
    private final StorageService storageService;
    private final QuestionImportWriter importWriter;

    public QuestionImportService(
            QuestionActorResolver actorResolver,
            StorageService storageService,
            QuestionImportWriter importWriter) {
        this.actorResolver = actorResolver;
        this.storageService = storageService;
        this.importWriter = importWriter;
    }

    public QuestionImportResultResponse importQuestions(
            MultipartFile excelFile,
            MultipartFile zipFile,
            JwtPrincipal principal) {

        if (!actorResolver.isHost(principal)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Bulk import is available to Host accounts only");
        }

        validateExcelFile(excelFile);
        Map<String, byte[]> audioCatalog = (zipFile != null && !zipFile.isEmpty())
                ? readZipCatalog(zipFile)
                : Map.of();

        List<Map<Integer, String>> rawRows = readExcelRows(excelFile);
        if (rawRows.size() < 2) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Import file must contain a header row and at least one data row");
        }

        List<String> headers = extractHeaders(rawRows.get(0));
        List<QuestionImportRowError> headerErrors = validateHeaders(headers);
        if (!headerErrors.isEmpty()) {
            throw new QuestionImportValidationException("Import file header row is invalid", headerErrors);
        }

        List<Map<String, String>> dataRows = extractDataRows(headers, rawRows);
        if (dataRows.size() > QuestionBankApiConstants.IMPORT_MAX_ROWS) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Import file exceeds the " + QuestionBankApiConstants.IMPORT_MAX_ROWS + " row limit");
        }

        List<QuestionImportRowError> errors = new ArrayList<>();
        List<ParsedQuestionImportRow> validRows = new ArrayList<>();
        for (int i = 0; i < dataRows.size(); i++) {
            int rowNumber = i + 2; // +1 for 0-index, +1 for the header row
            validateRow(rowNumber, dataRows.get(i), audioCatalog.keySet(), errors, validRows);
        }

        if (!errors.isEmpty()) {
            throw new QuestionImportValidationException("Import file failed row validation", errors);
        }

        // Upload every referenced audio file before opening any DB transaction (Design
        // Constraint: uploads are not part of the DB atomicity guarantee, but must all
        // succeed — or the whole import aborts — before any row is written).
        Map<Integer, UploadResponse> uploadsByRow = uploadAudioForRows(validRows, audioCatalog);

        UUID tenantId = actorResolver.resolveCallerTenantId(principal);
        UUID createdBy = actorResolver.resolveActorPublicId(principal);

        int imported = importWriter.writeAll(validRows, uploadsByRow, tenantId, createdBy);

        return new QuestionImportResultResponse(imported, 0, List.of());
    }

    // ── File validation ──────────────────────────────────────────────

    private void validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Import Excel file is required");
        }
        if (file.getSize() > QuestionBankApiConstants.IMPORT_MAX_EXCEL_SIZE_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Import Excel file exceeds the size limit");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(QuestionBankApiConstants.EXCEL_EXTENSION_XLSX)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Only .xlsx files are supported");
        }
    }

    // ── Excel parsing (mirrors ImportParseService's EasyExcel usage) ────

    private List<Map<Integer, String>> readExcelRows(MultipartFile file) {
        try {
            return EasyExcel.read(file.getInputStream())
                    .headRowNumber(0)
                    .sheet()
                    .doReadSync();
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Unable to read Excel file");
        } catch (RuntimeException exception) {
            LOGGER.warn("Question import failed while reading Excel file={}", file.getOriginalFilename(), exception);
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Unable to parse Excel file content");
        }
    }

    private List<String> extractHeaders(Map<Integer, String> headerRow) {
        List<String> headers = new ArrayList<>();
        for (String header : headerRow.values()) {
            if (header != null && !header.isBlank()) {
                headers.add(header.trim());
            }
        }
        return headers;
    }

    private List<QuestionImportRowError> validateHeaders(List<String> headers) {
        List<QuestionImportRowError> errors = new ArrayList<>();
        for (String required : QuestionBankApiConstants.IMPORT_REQUIRED_HEADERS) {
            if (!headers.contains(required)) {
                errors.add(new QuestionImportRowError(1, required, "Missing required column: " + required));
            }
        }
        return errors;
    }

    private List<Map<String, String>> extractDataRows(List<String> headers, List<Map<Integer, String>> rawRows) {
        List<Map<String, String>> parsedRows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rawRows.size(); rowIndex++) {
            Map<Integer, String> rawRow = rawRows.get(rowIndex);
            Map<String, String> parsedRow = new LinkedHashMap<>();
            for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                String value = rawRow.get(columnIndex);
                parsedRow.put(headers.get(columnIndex), value == null ? "" : value.trim());
            }
            boolean hasAnyValue = parsedRow.values().stream().anyMatch(v -> v != null && !v.isBlank());
            if (hasAnyValue) {
                parsedRows.add(parsedRow);
            }
        }
        return parsedRows;
    }

    // ── Row validation ───────────────────────────────────────────────

    private void validateRow(
            int rowNumber,
            Map<String, String> row,
            java.util.Set<String> audioCatalogFilenames,
            List<QuestionImportRowError> errors,
            List<ParsedQuestionImportRow> validRows) {

        String questionText = blankToNull(row.get("question_text"));
        String skillType = blankToNull(row.get("skill_type"));
        String correctAnswer = blankToNull(row.get("correct_answer"));
        String distractor1 = blankToNull(row.get("distractor_1"));
        String distractor2 = blankToNull(row.get("distractor_2"));
        String distractor3 = blankToNull(row.get("distractor_3"));
        String audioFilename = blankToNull(row.get("audio_filename"));

        int errorsBefore = errors.size();

        if (questionText == null) {
            errors.add(new QuestionImportRowError(rowNumber, "question_text", "question_text is required"));
        }

        Skill skill = null;
        if (skillType == null) {
            errors.add(new QuestionImportRowError(rowNumber, "skill_type", "skill_type is required"));
        } else {
            try {
                skill = Skill.valueOf(skillType.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                skill = null;
            }
            if (skill == null || !QuestionBankApiConstants.IMPORT_ALLOWED_SKILLS.contains(skill)) {
                errors.add(new QuestionImportRowError(rowNumber, "skill_type", "skill_type must be Reading or Listening"));
                skill = null;
            }
        }

        if (correctAnswer == null) {
            errors.add(new QuestionImportRowError(rowNumber, "correct_answer", "correct_answer is required"));
        }
        if (distractor1 == null) {
            errors.add(new QuestionImportRowError(rowNumber, "distractor_1", "at least one distractor is required"));
        }

        List<String> distractors = new ArrayList<>();
        if (distractor1 != null) {
            distractors.add(distractor1);
        }
        if (distractor2 != null) {
            distractors.add(distractor2);
        }
        if (distractor3 != null) {
            distractors.add(distractor3);
        }
        if (correctAnswer != null && distractors.contains(correctAnswer)) {
            errors.add(new QuestionImportRowError(rowNumber, "correct_answer", "correct_answer must not duplicate a distractor"));
        }

        if (audioFilename != null && !audioCatalogFilenames.contains(audioFilename)) {
            errors.add(new QuestionImportRowError(rowNumber, "audio_filename", "audio_filename not found in the uploaded ZIP"));
        } else if (audioFilename != null
                && !QuestionBankApiConstants.AUDIO_ALLOWED_MIME_TYPES.contains(probeAudioMimeType(audioFilename))) {
            errors.add(new QuestionImportRowError(
                    rowNumber, "audio_filename", "Unsupported audio format — use .wav, .mp3, or .m4a"));
        }

        if (errors.size() > errorsBefore) {
            return;
        }

        List<String> options = new ArrayList<>();
        options.add(correctAnswer);
        options.addAll(distractors);

        validRows.add(new ParsedQuestionImportRow(rowNumber, skill, questionText, options, correctAnswer, audioFilename));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    // ── ZIP handling (stream entries, cap cumulative size to avoid a zip-bomb) ──

    private Map<String, byte[]> readZipCatalog(MultipartFile zipFile) {
        if (zipFile.getSize() > QuestionBankApiConstants.IMPORT_MAX_ZIP_SIZE_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Import audio ZIP exceeds the size limit");
        }

        Map<String, byte[]> catalog = new HashMap<>();
        long totalUncompressedBytes = 0;

        try (InputStream in = zipFile.getInputStream(); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] buffer = new byte[8192];
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    totalUncompressedBytes += read;
                    if (totalUncompressedBytes > QuestionBankApiConstants.IMPORT_MAX_ZIP_SIZE_BYTES) {
                        throw new ApiException(ErrorCode.VALIDATION_ERROR,
                                "Import audio ZIP exceeds the uncompressed size limit");
                    }
                    out.write(buffer, 0, read);
                }
                String entryName = entry.getName();
                int lastSlash = entryName.lastIndexOf('/');
                String filename = lastSlash >= 0 ? entryName.substring(lastSlash + 1) : entryName;
                catalog.put(filename, out.toByteArray());
            }
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Unable to read audio ZIP file");
        }

        return catalog;
    }

    // ── Audio upload (outside any DB transaction) ────────────────────

    private Map<Integer, UploadResponse> uploadAudioForRows(
            List<ParsedQuestionImportRow> rows,
            Map<String, byte[]> audioCatalog) {

        Map<Integer, UploadResponse> uploads = new HashMap<>();
        for (ParsedQuestionImportRow row : rows) {
            if (row.audioFilename() == null) {
                continue;
            }
            byte[] content = audioCatalog.get(row.audioFilename());
            String contentType = probeAudioMimeType(row.audioFilename());
            MultipartFile wrapped = new ByteArrayMultipartFile(
                    "file", row.audioFilename(), contentType, content);
            try {
                UploadResponse uploaded = storageService.uploadFile(wrapped, StorageConstants.FOLDER_QUESTIONS);
                uploads.put(row.rowNumber(), uploaded);
            } catch (StorageException exception) {
                throw new QuestionImportValidationException(
                        "Audio upload failed during import",
                        List.of(new QuestionImportRowError(
                                row.rowNumber(), "audio_filename", "upload failed: " + exception.getMessage())));
            }
        }
        return uploads;
    }

    private String probeAudioMimeType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lower.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lower.endsWith(".m4a") || lower.endsWith(".mp4")) {
            return "audio/mp4";
        }
        return "application/octet-stream";
    }
}
