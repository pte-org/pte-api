package com.aptis.modules.examdelivery.interfaces;

import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examdelivery.dto.ExamAttemptResponse;
import com.aptis.modules.examdelivery.dto.SubmitExamRequest;

public interface ExamDeliveryOperations {

    ExamAttemptResponse submitAttempt(SubmitExamRequest request, JwtPrincipal principal);
}
