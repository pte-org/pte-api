package com.aptis.modules.examoperations.service;

import org.springframework.stereotype.Service;

import com.aptis.modules.examoperations.repository.ExamRepository;

@Service
public class ExamService {

    private final ExamRepository examRepository;

    public ExamService(ExamRepository examRepository) {
        this.examRepository = examRepository;
    }

    // Skeleton — implement later
}
