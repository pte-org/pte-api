package com.aptis.modules.examoperations.interfaces;

import com.aptis.modules.examoperations.dto.CreateExamRequest;
import com.aptis.modules.examoperations.dto.ExamResponse;

public interface ExamOperations {

    ExamResponse createExam(CreateExamRequest request);
}
