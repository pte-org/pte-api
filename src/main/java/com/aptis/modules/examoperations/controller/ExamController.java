package com.aptis.modules.examoperations.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptis.modules.examoperations.constant.ExamOperationsApiConstants;
import com.aptis.modules.examoperations.service.ExamService;

@RestController
@RequestMapping(ExamOperationsApiConstants.EXAMS)
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    // Skeleton — endpoints implemented later
}
