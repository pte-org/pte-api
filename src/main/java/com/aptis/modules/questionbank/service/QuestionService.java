package com.aptis.modules.questionbank.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.modules.iam.repository.AdminRepository;
import com.aptis.modules.examoperations.repository.ExamQuestionRepository;
import com.aptis.modules.questionbank.domain.DifficultyLevel;
import com.aptis.modules.questionbank.domain.Question;
import com.aptis.modules.questionbank.domain.QuestionStatus;
import com.aptis.modules.questionbank.domain.QuestionType;
import com.aptis.modules.questionbank.domain.Skill;
import com.aptis.modules.questionbank.dto.request.CreateQuestionRequest;
import com.aptis.modules.questionbank.dto.request.UpdateQuestionRequest;
import com.aptis.modules.questionbank.dto.response.QuestionResponse;
import com.aptis.modules.questionbank.interfaces.QuestionOperations;
import com.aptis.modules.storage.interfaces.StorageService;
import com.aptis.modules.storage.dto.response.UploadResponse;
import com.aptis.modules.asset.interfaces.AssetOperations;
import com.aptis.modules.asset.dto.request.CreateAssetRequest;
import com.aptis.modules.asset.domain.AssetType;
import com.aptis.modules.asset.dto.response.AssetResponse;
import com.aptis.modules.questionbank.repository.QuestionRepository;
import com.aptis.modules.questionbank.repository.QuestionSpecification;

@Service
public class QuestionService implements QuestionOperations {

    private final QuestionRepository questionRepository;
    private final AdminRepository adminRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final StorageService storageService;
    private final AssetOperations assetOperations;

    public QuestionService(
            QuestionRepository questionRepository,
            AdminRepository adminRepository,
            ExamQuestionRepository examQuestionRepository,
            StorageService storageService,
            AssetOperations assetOperations) {
        this.questionRepository = questionRepository;
        this.adminRepository = adminRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.storageService = storageService;
        this.assetOperations = assetOperations;
    }

    @Transactional(readOnly = true)
    public QuestionResponse getQuestion(UUID publicId) {
        Question question = questionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        java.util.List<AssetResponse> assets = assetOperations.getAssets(question.getAssetIds());
        return QuestionResponse.from(question, assets);
    }

    @Transactional
    public QuestionResponse createQuestion(CreateQuestionRequest request, Long userId) {
        validateOptions(request.questionType(), request.options(), request.correctAnswers());

        UUID createdBy = resolveUserPublicId(userId);

        Question question = new Question();
        applyFields(question, request);
        question.setCreatedBy(createdBy);
        question.setVersion(1);
        question.setIsCurrent(true);
        question.setIsImmutable(false);
        question.setStatus(QuestionStatus.DRAFT);

        Question saved = questionRepository.save(question);

        java.util.List<AssetResponse> assets = assetOperations.getAssets(saved.getAssetIds());
        return QuestionResponse.from(saved, assets);
    }

    @Transactional
    public QuestionResponse updateQuestion(UUID publicId, UpdateQuestionRequest request, Long userId) {
        validateOptions(request.questionType(), request.options(), request.correctAnswers());

        Question existing = questionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (Boolean.TRUE.equals(existing.getIsImmutable())) {
            return createNewVersion(existing, request, userId);
        }

        applyFields(existing, request);
        Question saved = questionRepository.save(existing);
        
        java.util.List<AssetResponse> assets = assetOperations.getAssets(saved.getAssetIds());
        return QuestionResponse.from(saved, assets);
    }

    @Transactional
    public void deleteQuestion(UUID publicId) {
        Question question = questionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        // BR-009: Prevent deletion if used in assigned batch
        if (examQuestionRepository.existsByQuestionIdAndIsUsedInAssignedBatchTrue(question.getId())) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT);
        }

        if (Boolean.TRUE.equals(question.getIsImmutable())) {
            // Soft-delete: archive immutable questions
            question.setStatus(QuestionStatus.ARCHIVED);
            question.setIsCurrent(false);
            questionRepository.save(question);
        } else {
            questionRepository.delete(question);
        }
    }

    @Transactional(readOnly = true)
    public Page<QuestionResponse> listQuestions(
            Skill skill,
            Integer part,
            QuestionType questionType,
            DifficultyLevel difficultyLevel,
            QuestionStatus status,
            Boolean isCurrent,
            Pageable pageable) {

        Specification<Question> spec = QuestionSpecification.buildFilter(
                skill, part, questionType, difficultyLevel, status, isCurrent);

        return questionRepository.findAll(spec, pageable)
                .map(q -> {
                    java.util.List<AssetResponse> assets = assetOperations.getAssets(q.getAssetIds());
                    return QuestionResponse.from(q, assets);
                });
    }

    // ── Private helpers ──────────────────────────────────────────────

    private void validateOptions(String questionType, List<String> options, List<String> correctAnswers) {
        if (QuestionType.MULTIPLE_CHOICE.name().equals(questionType)) {
            if (correctAnswers == null || correctAnswers.isEmpty()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR);
            }
        }
    }

    private QuestionResponse createNewVersion(
            Question existing,
            UpdateQuestionRequest request,
            Long userId) {

        Long rootId = existing.getParentId() != null
                ? existing.getParentId()
                : existing.getId();

        questionRepository.deactivateVersionChain(rootId);

        UUID createdBy = resolveUserPublicId(userId);

        Question newVersion = new Question();
        applyFields(newVersion, request);
        newVersion.setVersion(existing.getVersion() + 1);
        newVersion.setParentId(rootId);
        newVersion.setIsCurrent(true);
        newVersion.setIsImmutable(false);
        newVersion.setStatus(QuestionStatus.DRAFT);
        newVersion.setCreatedBy(createdBy);

        Question saved = questionRepository.save(newVersion);
        
        java.util.List<AssetResponse> assets = assetOperations.getAssets(saved.getAssetIds());
        return QuestionResponse.from(saved, assets);
    }

    private UUID resolveUserPublicId(Long userId) {
        return adminRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND))
                .getPublicId();
    }

    private void applyFields(Question question, CreateQuestionRequest request) {
        question.setSkill(Skill.valueOf(request.skill()));
        question.setPart(request.part());
        question.setQuestionType(QuestionType.valueOf(request.questionType()));
        question.setContent(request.content());
        question.setInstruction(request.instruction());
        question.setScoreWeight(request.scoreWeight() != null ? request.scoreWeight() : 1.0f);
        question.setExplanation(request.explanation());
        question.setTimeLimit(request.timeLimit());
        question.setPrepTime(request.prepTime());
        question.setMaxPlayCount(request.maxPlayCount());
        question.setAssetIds(request.assetIds() != null ? request.assetIds() : List.of());
        
        question.setDifficultyLevel(DifficultyLevel.valueOf(request.difficultyLevel()));
        question.setTopicTags(request.topicTags() != null ? request.topicTags() : List.of());
        question.setOptions(request.options() != null ? request.options() : List.of());
        question.setCorrectAnswers(request.correctAnswers() != null ? request.correctAnswers() : List.of());
    }

    private void applyFields(Question question, UpdateQuestionRequest request) {
        question.setSkill(Skill.valueOf(request.skill()));
        question.setPart(request.part());
        question.setQuestionType(QuestionType.valueOf(request.questionType()));
        question.setContent(request.content());
        question.setInstruction(request.instruction());
        question.setScoreWeight(request.scoreWeight() != null ? request.scoreWeight() : 1.0f);
        question.setExplanation(request.explanation());
        question.setTimeLimit(request.timeLimit());
        question.setPrepTime(request.prepTime());
        question.setMaxPlayCount(request.maxPlayCount());
        question.setAssetIds(request.assetIds() != null ? request.assetIds() : List.of());

        question.setDifficultyLevel(DifficultyLevel.valueOf(request.difficultyLevel()));
        question.setTopicTags(request.topicTags() != null ? request.topicTags() : List.of());
        question.setOptions(request.options() != null ? request.options() : List.of());
        question.setCorrectAnswers(request.correctAnswers() != null ? request.correctAnswers() : List.of());
    }
}
