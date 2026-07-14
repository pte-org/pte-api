package com.aptis.modules.iam.service.studentimport;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.excel.EasyExcel;
import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.domain.studentimport.ImportSession;
import com.aptis.modules.iam.dto.request.studentimport.PrepareImportRequest;
import com.aptis.modules.iam.dto.response.studentimport.ParseFileResponse;
import com.aptis.modules.iam.interfaces.StudentImportParser;

@Service
public class ImportParseService implements StudentImportParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportParseService.class);

    private final ImportSessionCache importSessionCache;

    public ImportParseService(ImportSessionCache importSessionCache) {
        this.importSessionCache = importSessionCache;
    }

    @Override
    public ParseFileResponse parseExcelFile(
            MultipartFile file,
            String hostId,
            Long organizationId) {
        validateFile(file);
        List<Map<Integer, String>> rawRows = readRows(file);
        LOGGER.info(
                "Student import parse read file={}, sizeBytes={}, rawRows={}",
                file.getOriginalFilename(),
                file.getSize(),
                rawRows.size());
        if (rawRows.size() < 2) {
            throw validationError("Import file must contain a header row and at least one data row");
        }

        List<String> headers = extractHeaders(rawRows.get(0));
        if (headers.isEmpty()) {
            throw validationError("Import file header row is empty");
        }
        List<Map<String, String>> parsedRows = extractDataRows(headers, rawRows);
        LOGGER.info(
                "Student import parse accepted file={}, headers={}, dataRows={}",
                file.getOriginalFilename(),
                headers.size(),
                parsedRows.size());
        return saveSession(hostId, organizationId, headers, parsedRows);
    }

    @Override
    public ParseFileResponse prepareCleanedRows(
            PrepareImportRequest request,
            String hostId,
            Long organizationId) {
        List<String> headers = request.columnHeaders().stream()
                .filter(header -> header != null && !header.isBlank())
                .map(String::trim)
                .toList();
        if (headers.isEmpty()) {
            throw validationError("Import file header row is empty");
        }

        List<Map<String, String>> parsedRows = new ArrayList<>();
        for (Map<String, String> row : request.rows()) {
            Map<String, String> parsedRow = mapPreparedRow(headers, row);
            if (hasAnyValue(parsedRow)) {
                if (parsedRows.size() >= IamApiConstants.IMPORT_MAX_ROWS) {
                    throw validationError("Import file exceeds the 10,000 row limit");
                }
                parsedRows.add(parsedRow);
            }
        }
        if (parsedRows.isEmpty()) {
            throw validationError("Import file has no data rows");
        }
        LOGGER.info(
                "Student import prepare accepted file={}, headers={}, dataRows={}",
                request.fileName(),
                headers.size(),
                parsedRows.size());
        return saveSession(hostId, organizationId, headers, parsedRows);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw validationError("Import file is required");
        }
        if (file.getSize() > IamApiConstants.IMPORT_MAX_FILE_SIZE_BYTES) {
            throw validationError("Import file exceeds the 50MB size limit");
        }
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw validationError("Import filename is missing");
        }
        String lowercaseFilename = filename.toLowerCase(Locale.ROOT);
        if (!lowercaseFilename.endsWith(IamApiConstants.EXCEL_EXTENSION_XLS)
                && !lowercaseFilename.endsWith(IamApiConstants.EXCEL_EXTENSION_XLSX)) {
            throw validationError("Only .xls and .xlsx files are supported");
        }
    }

    private List<Map<Integer, String>> readRows(MultipartFile file) {
        try {
            return EasyExcel.read(file.getInputStream())
                    .headRowNumber(0)
                    .sheet()
                    .doReadSync();
        } catch (IOException exception) {
            throw validationError("Unable to read Excel file");
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Student import parse failed while reading Excel file={}",
                    file.getOriginalFilename(),
                    exception);
            throw validationError("Unable to parse Excel file content");
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

    private List<Map<String, String>> extractDataRows(
            List<String> headers,
            List<Map<Integer, String>> rawRows) {
        List<Map<String, String>> parsedRows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rawRows.size(); rowIndex++) {
            Map<String, String> parsedRow = mapRow(headers, rawRows.get(rowIndex));
            if (hasAnyValue(parsedRow)) {
                if (parsedRows.size() >= IamApiConstants.IMPORT_MAX_ROWS) {
                    throw validationError("Import file exceeds the 10,000 row limit");
                }
                parsedRows.add(parsedRow);
            }
        }
        if (parsedRows.isEmpty()) {
            throw validationError("Import file has no data rows");
        }
        return parsedRows;
    }

    private boolean hasAnyValue(Map<String, String> parsedRow) {
        return parsedRow.values().stream()
                .anyMatch(value -> value != null && !value.isBlank());
    }

    private Map<String, String> mapRow(
            List<String> headers,
            Map<Integer, String> rawRow) {
        Map<String, String> parsedRow = new LinkedHashMap<>();
        for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
            String value = rawRow.get(columnIndex);
            if (value != null && !value.isBlank()) {
                parsedRow.put(headers.get(columnIndex), value.trim());
            } else {
                parsedRow.put(headers.get(columnIndex), "");
            }
        }
        return parsedRow;
    }

    private Map<String, String> mapPreparedRow(
            List<String> headers,
            Map<String, String> rawRow) {
        Map<String, String> parsedRow = new LinkedHashMap<>();
        for (String header : headers) {
            String value = rawRow.get(header);
            parsedRow.put(header, value == null ? "" : value.trim());
        }
        return parsedRow;
    }

    private ParseFileResponse saveSession(
            String hostId,
            Long organizationId,
            List<String> headers,
            List<Map<String, String>> parsedRows) {
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime expiresAt = createdAt.plusMinutes(IamApiConstants.IMPORT_SESSION_TTL_MINUTES);
        String importId = UUID.randomUUID().toString();

        ImportSession session = new ImportSession(
                importId,
                hostId,
                organizationId,
                headers,
                parsedRows,
                createdAt,
                expiresAt);
        importSessionCache.saveSession(session);

        return new ParseFileResponse(
                importId,
                headers,
                parsedRows.stream().limit(IamApiConstants.IMPORT_SAMPLE_ROW_COUNT).toList(),
                parsedRows.size(),
                expiresAt);
    }

    private ApiException validationError(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }
}
