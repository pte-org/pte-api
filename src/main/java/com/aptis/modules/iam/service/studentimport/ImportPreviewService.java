package com.aptis.modules.iam.service.studentimport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.modules.iam.constant.IamMessageConstants;
import com.aptis.modules.iam.domain.enums.ImportSessionState;
import com.aptis.modules.iam.domain.studentimport.ImportPreviewRow;
import com.aptis.modules.iam.domain.studentimport.ImportSession;
import com.aptis.modules.iam.domain.studentimport.ImportUsernamePatternConfig;
import com.aptis.modules.iam.dto.request.studentimport.PreviewRequest;
import com.aptis.modules.iam.dto.response.studentimport.PreviewResponse;
import com.aptis.modules.iam.dto.response.studentimport.PreviewRow;
import com.aptis.modules.iam.interfaces.StudentImportPreviewer;
import com.aptis.modules.iam.repository.StudentRepository;
import com.aptis.modules.iam.service.studentimport.UsernameCollisionResolver.ResolvedUsername;
import com.aptis.modules.iam.service.studentimport.UsernameGenerationService.UsernameBase;

@Service
public class ImportPreviewService implements StudentImportPreviewer {

    private static final int QUERY_CHUNK_SIZE = 1000;

    private final ImportSessionCache importSessionCache;
    private final UsernameGenerationService usernameGenerationService;
    private final UsernameCollisionResolver usernameCollisionResolver;
    private final StudentImportValidator studentImportValidator;
    private final StudentRepository studentRepository;

    public ImportPreviewService(
            ImportSessionCache importSessionCache,
            UsernameGenerationService usernameGenerationService,
            UsernameCollisionResolver usernameCollisionResolver,
            StudentImportValidator studentImportValidator,
            StudentRepository studentRepository) {
        this.importSessionCache = importSessionCache;
        this.usernameGenerationService = usernameGenerationService;
        this.usernameCollisionResolver = usernameCollisionResolver;
        this.studentImportValidator = studentImportValidator;
        this.studentRepository = studentRepository;
    }

    @Override
    public PreviewResponse preview(
            String hostId,
            Long organizationId,
            PreviewRequest request) {
        ImportSession session = loadSession(hostId, organizationId, request.importId());
        List<Map<String, String>> fieldRows = mapFieldRows(session, request.columnMappings());
        ImportUsernamePatternConfig usernamePatternConfig = toDomainConfig(request);
        List<UsernameBase> usernameBases = generateUsernameBases(session, fieldRows, usernamePatternConfig);
        List<ResolvedUsername> resolvedUsernames = usernameCollisionResolver.resolve(
                usernameBases.stream().map(UsernameBase::value).toList());
        Set<String> existingEmails = findExistingEmails(fieldRows, organizationId);

        List<ImportPreviewRow> previewRows = buildPreviewRows(
                fieldRows,
                usernameBases,
                resolvedUsernames,
                existingEmails);
        int errorCount = (int) previewRows.stream().filter(ImportPreviewRow::hasError).count();
        int fallbackCount = (int) usernameBases.stream().filter(UsernameBase::fallbackUsed).count();

        session.setColumnMappings(request.columnMappings());
        session.setUsernamePatternConfig(usernamePatternConfig);
        session.setPreviewRows(previewRows);
        if (!session.transitionState(ImportSessionState.PARSED, ImportSessionState.PREVIEWED)) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT);
        }
        importSessionCache.saveSession(session);

        List<PreviewRow> responseRows = previewRows.stream()
                .map(this::toResponseRow)
                .toList();
        return new PreviewResponse(
                responseRows,
                responseRows.size(),
                errorCount,
                errorCount > 0,
                fallbackCount);
    }

    private ImportSession loadSession(
            String hostId,
            Long organizationId,
            String importId) {
        return importSessionCache.getSession(hostId, organizationId, importId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED));
    }

    private List<Map<String, String>> mapFieldRows(
            ImportSession session,
            Map<String, String> columnMappings) {
        return session.getParsedRows().stream()
                .map(row -> mapFieldRow(row, columnMappings))
                .toList();
    }

    private Map<String, String> mapFieldRow(
            Map<String, String> originalRow,
            Map<String, String> columnMappings) {
        Map<String, String> fieldValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> mapping : columnMappings.entrySet()) {
            if (mapping.getValue() != null && !mapping.getValue().isBlank()) {
                fieldValues.put(mapping.getValue(), originalRow.getOrDefault(mapping.getKey(), ""));
            }
        }
        return fieldValues;
    }

    private List<UsernameBase> generateUsernameBases(
            ImportSession session,
            List<Map<String, String>> fieldRows,
            ImportUsernamePatternConfig usernamePatternConfig) {
        List<UsernameBase> usernameBases = new ArrayList<>();
        for (int index = 0; index < session.getParsedRows().size(); index++) {
            usernameBases.add(usernameGenerationService.generateBase(
                    session.getParsedRows().get(index),
                    fieldRows.get(index),
                    usernamePatternConfig,
                    session.getOrganizationId(),
                    index + 1));
        }
        return usernameBases;
    }

    private Set<String> findExistingEmails(
            List<Map<String, String>> fieldRows,
            Long organizationId) {
        Set<String> emails = new LinkedHashSet<>();
        for (Map<String, String> fieldRow : fieldRows) {
            String email = fieldRow.get("email");
            if (email != null && !email.isBlank()) {
                emails.add(email);
            }
        }

        Set<String> existingEmails = new LinkedHashSet<>();
        List<String> emailValues = new ArrayList<>(emails);
        for (int index = 0; index < emailValues.size(); index += QUERY_CHUNK_SIZE) {
            Set<String> chunk = new LinkedHashSet<>(
                    emailValues.subList(index, Math.min(index + QUERY_CHUNK_SIZE, emailValues.size())));
            if (!chunk.isEmpty()) {
                existingEmails.addAll(studentRepository.findExistingEmails(chunk, organizationId));
            }
        }
        return existingEmails;
    }

    private List<ImportPreviewRow> buildPreviewRows(
            List<Map<String, String>> fieldRows,
            List<UsernameBase> usernameBases,
            List<ResolvedUsername> resolvedUsernames,
            Set<String> existingEmails) {
        List<ImportPreviewRow> previewRows = new ArrayList<>();
        for (int index = 0; index < fieldRows.size(); index++) {
            Map<String, String> fieldValues = fieldRows.get(index);
            ResolvedUsername resolvedUsername = resolvedUsernames.get(index);
            List<String> warningCodes = buildWarningCodes(usernameBases.get(index), resolvedUsername);
            String errorCode = findErrorCode(fieldValues, resolvedUsername, existingEmails);
            previewRows.add(new ImportPreviewRow(
                    index + 1,
                    resolvedUsername.generatedUsername(),
                    resolvedUsername.usernameBase(),
                    fieldValues,
                    errorCode,
                    warningCodes,
                    errorCode != null));
        }
        return previewRows;
    }

    private ImportUsernamePatternConfig toDomainConfig(PreviewRequest request) {
        return new ImportUsernamePatternConfig(
                request.usernamePatternConfig().type(),
                request.usernamePatternConfig().sourceColumn());
    }

    private PreviewRow toResponseRow(ImportPreviewRow row) {
        return new PreviewRow(
                row.rowNumber(),
                row.generatedUsername(),
                row.usernameBase(),
                row.fieldValues(),
                row.errorCode(),
                row.warningCodes(),
                row.hasError());
    }

    private List<String> buildWarningCodes(
            UsernameBase usernameBase,
            ResolvedUsername resolvedUsername) {
        List<String> warningCodes = new ArrayList<>();
        if (usernameBase.fallbackUsed()) {
            warningCodes.add(IamMessageConstants.USERNAME_STRATEGY_FALLBACK);
        }
        if (resolvedUsername.collisionResolved()) {
            warningCodes.add(IamMessageConstants.USERNAME_COLLISION_RESOLVED);
        }
        return warningCodes;
    }

    private String findErrorCode(
            Map<String, String> fieldValues,
            ResolvedUsername resolvedUsername,
            Set<String> existingEmails) {
        if (resolvedUsername.errorCode() != null) {
            return resolvedUsername.errorCode();
        }
        String email = fieldValues.get("email");
        if (email != null && existingEmails.contains(email)) {
            return IamMessageConstants.DUPLICATE_EMAIL;
        }
        return studentImportValidator
                .validateFieldValues(
                        fieldValues.get("fullName"),
                        email,
                        fieldValues.get("dateOfBirth"))
                .orElse(null);
    }
}
