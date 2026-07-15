package com.aptis.modules.examoperations.interfaces;

import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examoperations.dto.CreateExamRequest;
import com.aptis.modules.examoperations.dto.ExamDetailResponse;
import com.aptis.modules.examoperations.dto.ExamSettingsRequest;

public interface ExamOperations {

    ExamDetailResponse createExam(CreateExamRequest request, JwtPrincipal principal);

    ExamDetailResponse getExam(Long examId, JwtPrincipal principal);

    ExamDetailResponse updateSettings(Long examId, ExamSettingsRequest request, JwtPrincipal principal);

    /**
     * Pre-start check usable by exam-delivery: an exam marked proctor_required cannot
     * start until a proctor is assigned. No assignment mechanism exists until Phase 7,
     * so this is currently equivalent to "not proctor_required" — see ExamService for
     * the forward-compatibility note.
     */
    boolean isStartable(Long examId);
}
