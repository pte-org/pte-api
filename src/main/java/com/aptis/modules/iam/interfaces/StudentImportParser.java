package com.aptis.modules.iam.interfaces;

import org.springframework.web.multipart.MultipartFile;

import com.aptis.modules.iam.dto.request.studentimport.PrepareImportRequest;
import com.aptis.modules.iam.dto.response.studentimport.ParseFileResponse;

public interface StudentImportParser {
    ParseFileResponse parseExcelFile(MultipartFile file, String hostId, Long organizationId);

    ParseFileResponse prepareCleanedRows(PrepareImportRequest request, String hostId, Long organizationId);
}
