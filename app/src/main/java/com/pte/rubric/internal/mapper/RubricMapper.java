package com.pte.rubric.internal.mapper;

import com.pte.rubric.domain.RubricRevision;
import com.pte.rubric.dto.response.RubricResponse;

public final class RubricMapper {

    private RubricMapper() {
    }

    public static RubricResponse toResponse(RubricRevision revision) {
        return new RubricResponse(
                revision.getPublicId(),
                revision.getRubricKey(),
                revision.getName(),
                revision.getTaskTypeKey(),
                revision.getChannel().name(),
                revision.getVersion(),
                revision.getStatus().name(),
                revision.getAuthorUserPublicId(),
                revision.getChangeReason(),
                revision.getRejectionReason(),
                revision.getDocumentJson(),
                revision.getCanonicalHash(),
                revision.getEntityVersion(),
                revision.getSubmittedAt(),
                revision.getPublishedAt(),
                revision.getRetiredAt());
    }
}
