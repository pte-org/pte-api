package com.aptis.modules.iam.dto.response.studentimport;

import java.util.List;

public record ImportValidationErrorResponse(List<RowError> errors) {
}
