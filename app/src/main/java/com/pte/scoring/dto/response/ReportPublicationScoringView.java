package com.pte.scoring.dto.response;

import java.util.List;
import java.util.UUID;

public record ReportPublicationScoringView(UUID publicationPublicId, List<ReportScoringAnswerView> answers) {
}
