package com.aptis.modules.questionbank.dto.response;

import java.util.List;

public record QuestionImportValidationErrorResponse(List<QuestionImportRowError> errors) {
}
