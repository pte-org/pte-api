package com.aptis.modules.examdelivery.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.common.interfaces.StudentAttemptLookup;
import com.aptis.modules.examdelivery.repository.ExamAttemptRepository;

@Service
public class ExamAttemptLookupService implements StudentAttemptLookup {

    private final ExamAttemptRepository examAttemptRepository;

    public ExamAttemptLookupService(ExamAttemptRepository examAttemptRepository) {
        this.examAttemptRepository = examAttemptRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAttemptForExam(Long studentId, Long examId) {
        return examAttemptRepository.existsByStudentIdAndExamId(studentId.toString(), examId);
    }
}
