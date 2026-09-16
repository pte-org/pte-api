package com.pte.identity.internal.service;

import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.internal.exception.StudentLimitExceededException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentRosterImportServiceTest {

    @Mock
    private UserBulkCreateWriter bulkCreateWriter;

    @Mock
    private TenancyService tenancyService;

    private StudentRosterImportService importService;

    @BeforeEach
    void setUp() {
        importService = new StudentRosterImportService(new RosterFileService(), bulkCreateWriter, tenancyService);
    }

    @Test
    void importRoster_checksCapacityBeforeCreatingAndReturnsCredentialsInOriginalRows() throws IOException {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        byte[] input = workbook(2, "MSSV", "Fullname", "DOB");
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(bulkCreateWriter.createGeneratedStudent(anyString(), eq(tenantId))).thenAnswer(invocation -> {
            String username = invocation.getArgument(0);
            return Optional.of(new UserBulkCreateWriter.Result(user(username), "Abcd-2345"));
        });

        byte[] exported = importService.importRoster(file(input), caller);

        InOrder order = inOrder(tenancyService, bulkCreateWriter);
        order.verify(tenancyService).assertCanAddStudents(tenantId, 2L);
        order.verify(tenancyService).getTenantCode(tenantId);
        order.verify(bulkCreateWriter, times(2)).createGeneratedStudent(anyString(), eq(tenantId));
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("account");
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("password");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).startsWith("school.");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("Abcd-2345");
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).startsWith("school.");
        }
    }

    @Test
    void importRoster_whenFiveHundredRowsExceedCapacity_rejectsBeforeAnyWrite() throws IOException {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        doThrow(new StudentLimitExceededException(490L, 500L, 500L))
                .when(tenancyService).assertCanAddStudents(tenantId, 500L);

        assertThatThrownBy(() -> importService.importRoster(file(workbook(500, "Họ", "Tên", "Lớp")), caller))
                .isInstanceOf(StudentLimitExceededException.class)
                .hasMessage("STUDENT_LIMIT_EXCEEDED: current=490, limit=500, adding=500");

        verify(tenancyService, never()).getTenantCode(any());
        verify(bulkCreateWriter, never()).createGeneratedStudent(anyString(), any());
    }

    @Test
    void importRoster_sameFileTwice_createsTwoCredentialSetsWithoutDuplicateCheck() throws IOException {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        MockMultipartFile firstFile = file(workbook(2, "A", "B", "C"));
        MockMultipartFile secondFile = file(firstFile.getBytes());
        when(tenancyService.getTenantCode(tenantId)).thenReturn("school");
        when(bulkCreateWriter.createGeneratedStudent(anyString(), eq(tenantId))).thenAnswer(invocation ->
                Optional.of(new UserBulkCreateWriter.Result(user(invocation.getArgument(0)), "Abcd-2345")));

        byte[] first = importService.importRoster(firstFile, caller);
        byte[] second = importService.importRoster(secondFile, caller);

        assertThat(first).isNotEmpty();
        assertThat(second).isNotEmpty();
        verify(bulkCreateWriter, org.mockito.Mockito.times(4))
                .createGeneratedStudent(anyString(), eq(tenantId));
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "roster.xlsx", IdentityConstants.ROSTER_CONTENT_TYPE, bytes);
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        return user;
    }

    private byte[] workbook(int rowCount, String... headers) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Roster");
            var header = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                header.createCell(column).setCellValue(headers[column]);
            }
            for (int index = 1; index <= rowCount; index++) {
                var row = sheet.createRow(index);
                row.createCell(0).setCellValue("row-" + index);
                row.createCell(1).setCellValue("value-" + index);
                row.createCell(2).setCellValue("class-" + index);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
