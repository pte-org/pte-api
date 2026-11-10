package com.pte.itembank;

import com.pte.itembank.dto.response.ExamCandidateQuestion;
import com.pte.itembank.internal.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** The exam-ready questions of a task type, with their CEFR levels, for building exams by difficulty. */
@Service
public class ExamCandidateService {

    private final QuestionRepository questionRepository;

    public ExamCandidateService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    /** In a fixed order, so the same bank and seed always produce the same exam. */
    @Transactional(readOnly = true)
    public List<ExamCandidateQuestion> candidates(String taskTypeKey) {
        return questionRepository.findExamCandidatesByTaskTypeKey(taskTypeKey).stream()
                .map(row -> new ExamCandidateQuestion((UUID) row[0], row[1] == null ? null : row[1].toString()))
                .toList();
    }
}
