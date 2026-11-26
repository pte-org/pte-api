package com.pte.itembank.internal.service;

import com.pte.itembank.domain.Question;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.mapper.QuestionMapper;
import com.pte.itembank.internal.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The revision chain of a question. A published question is never edited in place: a revision is a new row in
 * the same group and the one it replaces is archived unchanged, so comparing two rows is the content history.
 */
@Service
public class QuestionRevisionQuery {

    private final QuestionRepository questionRepository;

    public QuestionRevisionQuery(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    /** @return every revision in the group of {@code publicId}, newest first, with full content and options */
    @Transactional(readOnly = true)
    public List<QuestionResponse> revisions(UUID publicId) {
        Question question = questionRepository.findWithOptionsByPublicId(publicId)
                .orElseThrow(QuestionNotFoundException::new);
        return questionRepository.findRevisionsWithOptions(question.getRevisionGroupPublicId()).stream()
                .map(QuestionMapper::toResponse)
                .toList();
    }
}
