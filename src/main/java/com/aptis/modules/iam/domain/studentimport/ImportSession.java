package com.aptis.modules.iam.domain.studentimport;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.aptis.modules.iam.domain.enums.ImportSessionState;

public class ImportSession {

    private final String importId;
    private final String hostId;
    private final Long organizationId;
    private final List<String> columnHeaders;
    private final List<Map<String, String>> parsedRows;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;
    private final AtomicReference<ImportSessionState> state =
            new AtomicReference<>(ImportSessionState.PARSED);

    private Map<String, String> columnMappings;
    private ImportUsernamePatternConfig usernamePatternConfig;
    private List<ImportPreviewRow> previewRows;

    public ImportSession(
            String importId,
            String hostId,
            Long organizationId,
            List<String> columnHeaders,
            List<Map<String, String>> parsedRows,
            LocalDateTime createdAt,
            LocalDateTime expiresAt) {
        this.importId = importId;
        this.hostId = hostId;
        this.organizationId = organizationId;
        this.columnHeaders = List.copyOf(columnHeaders);
        this.parsedRows = List.copyOf(parsedRows);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getImportId() {
        return importId;
    }

    public String getHostId() {
        return hostId;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public List<String> getColumnHeaders() {
        return columnHeaders;
    }

    public List<Map<String, String>> getParsedRows() {
        return parsedRows;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public Map<String, String> getColumnMappings() {
        return columnMappings;
    }

    public void setColumnMappings(Map<String, String> columnMappings) {
        this.columnMappings = Map.copyOf(columnMappings);
    }

    public ImportUsernamePatternConfig getUsernamePatternConfig() {
        return usernamePatternConfig;
    }

    public void setUsernamePatternConfig(ImportUsernamePatternConfig usernamePatternConfig) {
        this.usernamePatternConfig = usernamePatternConfig;
    }

    public List<ImportPreviewRow> getPreviewRows() {
        return previewRows;
    }

    public void setPreviewRows(List<ImportPreviewRow> previewRows) {
        this.previewRows = List.copyOf(previewRows);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean transitionState(
            ImportSessionState expectedState,
            ImportSessionState nextState) {
        return state.compareAndSet(expectedState, nextState);
    }

    public ImportSessionState getState() {
        return state.get();
    }
}
