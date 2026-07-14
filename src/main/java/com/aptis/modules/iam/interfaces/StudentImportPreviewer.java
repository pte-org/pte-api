package com.aptis.modules.iam.interfaces;

import com.aptis.modules.iam.dto.request.studentimport.PreviewRequest;
import com.aptis.modules.iam.dto.response.studentimport.PreviewResponse;

public interface StudentImportPreviewer {
    PreviewResponse preview(String hostId, Long organizationId, PreviewRequest request);
}
