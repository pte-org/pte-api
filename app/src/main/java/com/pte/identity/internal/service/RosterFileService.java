package com.pte.identity.internal.service;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.exception.InvalidRosterFileException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/** Reads and writes roster workbooks without interpreting or persisting roster columns. */
@Service
public class RosterFileService {

    private static final int BUFFER_SIZE = 8 * 1024;

    /** Reads an upload into bounded memory and validates its filename and XLSX signature. */
    public byte[] readAndValidate(MultipartFile file) {
        if (file == null) {
            throw new InvalidRosterFileException(IdentityConstants.ROSTER_FILE_REQUIRED);
        }
        if (file.isEmpty()) {
            throw new InvalidRosterFileException(IdentityConstants.ROSTER_FILE_EMPTY);
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new InvalidRosterFileException(IdentityConstants.ROSTER_FILE_EXTENSION_INVALID);
        }
        if (file.getSize() > IdentityConstants.ROSTER_MAX_FILE_SIZE_BYTES) {
            throw tooLarge();
        }

        try (InputStream input = file.getInputStream()) {
            long reportedSize = Math.max(0L, file.getSize());
            int initialCapacity = (int) Math.min(reportedSize, IdentityConstants.ROSTER_MAX_FILE_SIZE_BYTES);
            ByteArrayOutputStream output = new ByteArrayOutputStream(initialCapacity);
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            long total = 0L;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > IdentityConstants.ROSTER_MAX_FILE_SIZE_BYTES) {
                    throw tooLarge();
                }
                output.write(buffer, 0, read);
            }

            byte[] bytes = output.toByteArray();
            if (!hasZipSignature(bytes)) {
                throw new InvalidRosterFileException(IdentityConstants.ROSTER_FILE_TYPE_INVALID);
            }
            return bytes;
        } catch (InvalidRosterFileException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new InvalidRosterFileException(IdentityConstants.ROSTER_FILE_TYPE_INVALID);
        }
    }

    /** Counts non-empty rows after the first row in the first worksheet. */
    public int countDataRows(InputStream input) {
        try (Workbook workbook = openWorkbook(input)) {
            Sheet sheet = firstSheet(workbook);
            int count = 0;
            for (Row row : sheet) {
                if (row.getRowNum() == 0 || !hasData(row)) {
                    continue;
                }
                count++;
                if (count > IdentityConstants.ROSTER_MAX_DATA_ROWS) {
                    throw rowLimitExceeded();
                }
            }
            return count;
        } catch (InvalidRosterFileException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new InvalidRosterFileException(IdentityConstants.ROSTER_FILE_TYPE_INVALID);
        }
    }

    /** Appends account/password columns to the first worksheet and returns the workbook bytes. */
    public byte[] appendCredentialColumns(InputStream input, List<Credential> credentials) {
        try (Workbook workbook = openWorkbook(input)) {
            Sheet sheet = firstSheet(workbook);
            int lastUsedColumn = lastUsedColumn(sheet);
            Row header = sheet.getRow(0);
            if (header == null) {
                header = sheet.createRow(0);
            }
            header.createCell(lastUsedColumn).setCellValue(IdentityConstants.ROSTER_ACCOUNT_HEADER);
            header.createCell(lastUsedColumn + 1).setCellValue(IdentityConstants.ROSTER_PASSWORD_HEADER);

            int credentialIndex = 0;
            for (Row row : sheet) {
                if (row.getRowNum() == 0 || !hasData(row)) {
                    continue;
                }
                if (credentialIndex >= credentials.size()) {
                    throw new InvalidRosterFileException(IdentityConstants.ROSTER_CREDENTIAL_COUNT_MISMATCH);
                }
                Credential credential = credentials.get(credentialIndex++);
                row.createCell(lastUsedColumn).setCellValue(credential.account());
                row.createCell(lastUsedColumn + 1).setCellValue(credential.password());
            }
            if (credentialIndex != credentials.size()) {
                throw new InvalidRosterFileException(IdentityConstants.ROSTER_CREDENTIAL_COUNT_MISMATCH);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        } catch (InvalidRosterFileException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new InvalidRosterFileException(IdentityConstants.ROSTER_IMPORT_FAILED);
        }
    }

    private Workbook openWorkbook(InputStream input) throws IOException {
        return new XSSFWorkbook(input);
    }

    private Sheet firstSheet(Workbook workbook) {
        if (workbook.getNumberOfSheets() == 0) {
            throw new InvalidRosterFileException(IdentityConstants.ROSTER_FILE_TYPE_INVALID);
        }
        return workbook.getSheetAt(0);
    }

    private int lastUsedColumn(Sheet sheet) {
        int lastUsedColumn = 0;
        for (Row row : sheet) {
            if (row.getLastCellNum() > lastUsedColumn) {
                lastUsedColumn = row.getLastCellNum();
            }
        }
        return lastUsedColumn;
    }

    private boolean hasData(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() == CellType.BLANK) {
                continue;
            }
            if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank()) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean hasZipSignature(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 0x50
                && bytes[1] == 0x4b
                && bytes[2] == 0x03
                && bytes[3] == 0x04;
    }

    private InvalidRosterFileException tooLarge() {
        return new InvalidRosterFileException(String.format(IdentityConstants.ROSTER_FILE_TOO_LARGE,
                IdentityConstants.ROSTER_MAX_FILE_SIZE_BYTES));
    }

    private InvalidRosterFileException rowLimitExceeded() {
        return new InvalidRosterFileException(String.format(IdentityConstants.ROSTER_ROW_LIMIT_EXCEEDED,
                IdentityConstants.ROSTER_MAX_DATA_ROWS));
    }

    public record Credential(String account, String password) {
    }
}
