package com.aptis.modules.iam.service.studentimport;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.alibaba.excel.EasyExcel;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.domain.studentimport.CredentialRow;

@Service
public class CredentialExcelWriter {

    public byte[] write(
            List<Map<String, String>> originalRows,
            List<String> originalHeaders,
            List<CredentialRow> credentialRows) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream)
                .head(buildHeaders(originalHeaders))
                .sheet(IamApiConstants.IMPORT_EXCEL_SHEET_NAME)
                .doWrite(buildRows(originalRows, originalHeaders, credentialRows));
        return outputStream.toByteArray();
    }

    private List<List<String>> buildHeaders(List<String> originalHeaders) {
        List<List<String>> headers = new ArrayList<>();
        for (String originalHeader : originalHeaders) {
            headers.add(List.of(originalHeader));
        }
        headers.add(List.of(IamApiConstants.IMPORT_USERNAME_COLUMN));
        headers.add(List.of(IamApiConstants.IMPORT_PASSWORD_COLUMN));
        return headers;
    }

    private List<List<String>> buildRows(
            List<Map<String, String>> originalRows,
            List<String> originalHeaders,
            List<CredentialRow> credentialRows) {
        List<List<String>> rows = new ArrayList<>();
        for (int index = 0; index < originalRows.size(); index++) {
            rows.add(buildRow(originalRows.get(index), originalHeaders, credentialRows.get(index)));
        }
        return rows;
    }

    private List<String> buildRow(
            Map<String, String> originalRow,
            List<String> originalHeaders,
            CredentialRow credentialRow) {
        List<String> row = new ArrayList<>();
        for (String header : originalHeaders) {
            row.add(originalRow.getOrDefault(header, ""));
        }
        row.add(credentialRow.username());
        row.add(credentialRow.plaintextPassword());
        return row;
    }
}
