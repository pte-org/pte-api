package com.pte.identity.internal.service;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.exception.InvalidRosterFileException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RosterFileServiceTest {

    private RosterFileService rosterFileService;

    @BeforeEach
    void setUp() {
        rosterFileService = new RosterFileService();
    }

    @Test
    void appendCredentialColumns_preservesThreeColumnsAndAddsTwoColumns() throws IOException {
        byte[] original = workbook(new String[]{"Họ", "Tên", "Lớp"},
                new String[]{"Nguyễn", "An", "12A1"},
                new String[]{"Trần", "Bình", "12A2"});

        byte[] exported = rosterFileService.appendCredentialColumns(new ByteArrayInputStream(original), List.of(
                new RosterFileService.Credential("school.aaaa1111", "Abcd-2345"),
                new RosterFileService.Credential("school.bbbb2222", "Efgh-6789")));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 5);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Họ");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("account");
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("password");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("12A1");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("school.aaaa1111");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("Abcd-2345");
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("school.bbbb2222");
            assertThat(sheet.getRow(2).getCell(4).getStringCellValue()).isEqualTo("Efgh-6789");
        }
    }

    @Test
    void countDataRows_ignoresHeaderAndBlankTrailingRows() throws IOException {
        byte[] original = workbook(new String[]{"MSSV", "Fullname", "DOB"},
                new String[]{"S-001", "Student One", "2008-01-01"},
                new String[]{"S-002", "Student Two", "2008-02-01"});

        assertThat(rosterFileService.countDataRows(new ByteArrayInputStream(original))).isEqualTo(2);
    }

    @Test
    void arbitraryHeaderNames_areAcceptedWithoutColumnMapping() throws IOException {
        byte[] original = workbook(new String[]{"MSSV", "Fullname", "DOB"},
                new String[]{"S-001", "Student One", "2008-01-01"});

        byte[] exported = rosterFileService.appendCredentialColumns(new ByteArrayInputStream(original), List.of(
                new RosterFileService.Credential("school.cccc3333", "Ijkl-2345")));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("MSSV");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Fullname");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("DOB");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("school.cccc3333");
        }
    }

    @Test
    void readAndValidate_rejectsNonXlsxExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "roster.xls", "application/vnd.ms-excel",
                new byte[]{0x50, 0x4b, 0x03, 0x04});

        assertThatThrownBy(() -> rosterFileService.readAndValidate(file))
                .isInstanceOf(InvalidRosterFileException.class)
                .hasMessage(IdentityConstants.ROSTER_FILE_EXTENSION_INVALID);
    }

    @Test
    void readAndValidate_rejectsOversizedFile() {
        MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                IdentityConstants.ROSTER_CONTENT_TYPE,
                new byte[(int) IdentityConstants.ROSTER_MAX_FILE_SIZE_BYTES + 1]);

        assertThatThrownBy(() -> rosterFileService.readAndValidate(file))
                .isInstanceOf(InvalidRosterFileException.class)
                .hasMessage("ROSTER_FILE_TOO_LARGE: maxBytes=10485760");
    }

    @Test
    void countDataRows_rejectsRowsAboveConfiguredLimit() throws IOException {
        byte[] original = workbook(IdentityConstants.ROSTER_MAX_DATA_ROWS + 1, "A", "B", "C");

        assertThatThrownBy(() -> rosterFileService.countDataRows(new ByteArrayInputStream(original)))
                .isInstanceOf(InvalidRosterFileException.class)
                .hasMessage("ROSTER_ROW_LIMIT_EXCEEDED: maxRows=10000");
    }

    private byte[] workbook(int rowCount, String... headers) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Roster");
            var header = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                header.createCell(column).setCellValue(headers[column]);
            }
            for (int rowIndex = 1; rowIndex <= rowCount; rowIndex++) {
                var row = sheet.createRow(rowIndex);
                row.createCell(0).setCellValue("row-" + rowIndex);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] workbook(String[] headers, String[]... dataRows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Roster");
            var header = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                header.createCell(column).setCellValue(headers[column]);
            }
            for (int rowIndex = 0; rowIndex < dataRows.length; rowIndex++) {
                var row = sheet.createRow(rowIndex + 1);
                for (int column = 0; column < dataRows[rowIndex].length; column++) {
                    row.createCell(column).setCellValue(dataRows[rowIndex][column]);
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
