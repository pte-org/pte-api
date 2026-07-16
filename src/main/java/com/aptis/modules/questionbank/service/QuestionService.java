package com.aptis.modules.questionbank.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examoperations.repository.ExamQuestionRepository;
import com.aptis.modules.questionbank.constant.QuestionBankApiConstants;
import com.aptis.modules.questionbank.domain.enums.DifficultyLevel;
import com.aptis.modules.questionbank.domain.Question;
import com.aptis.modules.questionbank.domain.enums.QuestionSource;
import com.aptis.modules.questionbank.domain.enums.QuestionStatus;
import com.aptis.modules.questionbank.domain.enums.QuestionType;
import com.aptis.modules.questionbank.domain.enums.Skill;
import com.aptis.modules.questionbank.dto.request.CreateQuestionRequest;
import com.aptis.modules.questionbank.dto.request.UpdateQuestionRequest;
import com.aptis.modules.questionbank.dto.response.QuestionResponse;
import com.aptis.modules.questionbank.interfaces.QuestionOperations;
import com.aptis.modules.storage.constant.StorageConstants;
import com.aptis.modules.storage.dto.response.UploadResponse;
import com.aptis.modules.storage.interfaces.StorageService;
import com.aptis.modules.asset.domain.AssetType;
import com.aptis.modules.asset.dto.request.CreateAssetRequest;
import com.aptis.modules.asset.interfaces.AssetOperations;
import com.aptis.modules.asset.dto.response.AssetResponse;
import com.aptis.modules.questionbank.repository.QuestionRepository;
import com.aptis.modules.questionbank.repository.QuestionSpecification;

import org.springframework.web.multipart.MultipartFile;

@Service
public class QuestionService implements QuestionOperations {

    private final QuestionRepository questionRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final StorageService storageService;
    private final AssetOperations assetOperations;
    private final QuestionActorResolver actorResolver;

    public QuestionService(
            QuestionRepository questionRepository,
            ExamQuestionRepository examQuestionRepository,
            StorageService storageService,
            AssetOperations assetOperations,
            QuestionActorResolver actorResolver) {
        this.questionRepository = questionRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.storageService = storageService;
        this.assetOperations = assetOperations;
        this.actorResolver = actorResolver;
    }

    @Transactional(readOnly = true)
    public QuestionResponse getQuestion(UUID publicId, JwtPrincipal principal) {
        Question question = questionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        UUID callerTenantId = actorResolver.resolveCallerTenantId(principal);
        if (!isVisibleTo(question, callerTenantId)) {
            // 404, not 403: do not confirm existence of another tenant's private content.
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        return toResponse(question);
    }

    @Transactional
    public QuestionResponse createQuestion(CreateQuestionRequest request, JwtPrincipal principal) {
        validateOptions(request.questionType(), request.options(), request.correctAnswers());

        UUID createdBy = actorResolver.resolveActorPublicId(principal);
        boolean isHost = actorResolver.isHost(principal);

        Question question = new Question();
        applyFields(question, request);
        question.setCreatedBy(createdBy);
        question.setVersion(1);
        question.setIsCurrent(true);
        question.setIsImmutable(false);
        question.setStatus(QuestionStatus.DRAFT);

        if (isHost) {
            question.setSource(QuestionSource.HOST);
            question.setTenantId(actorResolver.resolveCallerTenantId(principal));
        } else {
            question.setSource(QuestionSource.VENDOR);
            question.setTenantId(null);
        }

        Question saved = questionRepository.save(question);

        return toResponse(saved);
    }

    @Transactional
    public QuestionResponse updateQuestion(UUID publicId, UpdateQuestionRequest request, JwtPrincipal principal) {
        validateOptions(request.questionType(), request.options(), request.correctAnswers());

        Question existing = questionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        enforceOwnership(existing, principal);

        if (Boolean.TRUE.equals(existing.getIsImmutable())) {
            return createNewVersion(existing, request, principal);
        }

        applyFields(existing, request);
        Question saved = questionRepository.save(existing);

        return toResponse(saved);
    }

    @Transactional
    public void deleteQuestion(UUID publicId, JwtPrincipal principal) {
        Question question = questionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        enforceOwnership(question, principal);

        // BR-009: Prevent deletion if used in assigned batch
        if (examQuestionRepository.existsByQuestionIdAndIsUsedInAssignedBatchTrue(question.getId())) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT);
        }

        if (Boolean.TRUE.equals(question.getIsImmutable())) {
            // Soft-delete: archive immutable questions; keep the audio asset for the historical record.
            question.setStatus(QuestionStatus.ARCHIVED);
            question.setIsCurrent(false);
            questionRepository.save(question);
        } else {
            // Hard-delete: the audio asset has no DB-level cascade to the question row, so
            // clean it up explicitly to avoid orphaning storage.
            if (question.getAudioAssetId() != null) {
                AssetResponse audio = assetOperations.getAsset(question.getAudioAssetId());
                storageService.deleteFile(audio.storageKey());
                assetOperations.deleteAsset(audio.publicId());
            }
            questionRepository.delete(question);
        }
    }

    @Transactional
    public QuestionResponse publishQuestion(UUID publicId, JwtPrincipal principal) {
        Question question = questionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        enforceOwnership(question, principal);

        if (question.getStatus() != QuestionStatus.DRAFT) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }

        // Pre-publish validation: Listening/Speaking content must have an audio attachment.
        if ((question.getSkill() == Skill.LISTENING || question.getSkill() == Skill.SPEAKING)
                && question.getAudioAssetId() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }

        question.setStatus(QuestionStatus.ACTIVE);
        question.setPublishedAt(LocalDateTime.now());
        Question saved = questionRepository.save(question);

        return toResponse(saved);
    }

    /**
     * Not wrapped in a single @Transactional boundary on purpose: this method spans an
     * external Cloudinary call, which must not sit inside an open DB transaction. Instead,
     * the new file is uploaded and attached FIRST — only once the question is durably
     * pointing at the new asset do we remove the old one. If the new upload fails, the
     * existing audio is untouched; if only the old-asset cleanup fails afterward, the
     * question still has working audio (just a leaked old file), which is the strictly
     * safer failure mode.
     */
    public QuestionResponse uploadAudio(UUID publicId, MultipartFile file, JwtPrincipal principal) {
        Question question = questionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        enforceOwnership(question, principal);

        if (question.getSkill() != Skill.LISTENING && question.getSkill() != Skill.SPEAKING) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }

        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        if (file.getSize() > QuestionBankApiConstants.AUDIO_MAX_SIZE_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        if (!QuestionBankApiConstants.AUDIO_ALLOWED_MIME_TYPES.contains(file.getContentType())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }

        UUID previousAudioAssetId = question.getAudioAssetId();

        UploadResponse uploadResponse = storageService.uploadFile(file, StorageConstants.FOLDER_QUESTIONS);

        AssetResponse asset = assetOperations.createAsset(new CreateAssetRequest(
                AssetType.AUDIO_QUESTION,
                uploadResponse.storageKey(),
                uploadResponse.cdnUrl(),
                uploadResponse.filename(),
                uploadResponse.sizeBytes(),
                uploadResponse.mimeType(),
                actorResolver.resolveActorPublicId(principal),
                question.getTenantId(),
                null));

        question.setAudioAssetId(asset.publicId());
        Question saved = questionRepository.save(question);

        // Replace: only remove the previous audio asset once the new one is attached and saved.
        if (previousAudioAssetId != null) {
            AssetResponse previous = assetOperations.getAsset(previousAudioAssetId);
            storageService.deleteFile(previous.storageKey());
            assetOperations.deleteAsset(previous.publicId());
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<QuestionResponse> listQuestions(
            Skill skill,
            Integer part,
            QuestionType questionType,
            DifficultyLevel difficultyLevel,
            QuestionStatus status,
            Boolean isCurrent,
            JwtPrincipal principal,
            Pageable pageable) {

        UUID callerTenantId = actorResolver.resolveCallerTenantId(principal);
        Specification<Question> spec = QuestionSpecification
                .buildFilter(skill, part, questionType, difficultyLevel, status, isCurrent)
                .and(QuestionSpecification.visibleTo(callerTenantId));

        Page<Question> page = questionRepository.findAll(spec, pageable);

        // Batch-load every asset referenced anywhere on this page (including audio) in
        // one query, instead of one getAssets() call per question (N+1).
        List<UUID> pageAssetIds = page.getContent().stream()
                .flatMap(q -> {
                    List<UUID> ids = new java.util.ArrayList<>(
                            q.getAssetIds() == null ? List.of() : q.getAssetIds());
                    if (q.getAudioAssetId() != null) {
                        ids.add(q.getAudioAssetId());
                    }
                    return ids.stream();
                })
                .distinct()
                .collect(Collectors.toList());
        Map<UUID, AssetResponse> assetsById = assetOperations.getAssets(pageAssetIds).stream()
                .collect(Collectors.toMap(AssetResponse::publicId, a -> a));

        return page.map(q -> {
            List<AssetResponse> assets = q.getAssetIds() == null
                    ? List.of()
                    : q.getAssetIds().stream()
                            .map(assetsById::get)
                            .filter(java.util.Objects::nonNull)
                            .collect(Collectors.toList());
            AssetResponse audioAsset = q.getAudioAssetId() != null ? assetsById.get(q.getAudioAssetId()) : null;
            return QuestionResponse.from(q, assets, audioAsset);
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
            JwtPrincipal principal) {

        Long rootId = existing.getParentId() != null
                ? existing.getParentId()
                : existing.getId();

        questionRepository.deactivateVersionChain(rootId);

        Question newVersion = new Question();
        applyFields(newVersion, request);
        newVersion.setVersion(existing.getVersion() + 1);
        newVersion.setParentId(rootId);
        newVersion.setIsCurrent(true);
        newVersion.setIsImmutable(false);
        newVersion.setStatus(QuestionStatus.DRAFT);
        newVersion.setCreatedBy(actorResolver.resolveActorPublicId(principal));
        newVersion.setSource(existing.getSource());
        newVersion.setTenantId(existing.getTenantId());

        Question saved = questionRepository.save(newVersion);

        return toResponse(saved);
    }

    /** Loads referenced assets (including audio, if any) and assembles the response DTO for one question. */
    private QuestionResponse toResponse(Question question) {
        List<AssetResponse> assets = assetOperations.getAssets(question.getAssetIds());
        AssetResponse audioAsset = question.getAudioAssetId() != null
                ? assetOperations.getAsset(question.getAudioAssetId())
                : null;
        return QuestionResponse.from(question, assets, audioAsset);
    }

    private boolean isVisibleTo(Question question, UUID callerTenantId) {
        if (question.getTenantId() == null) {
            return true;
        }
        return callerTenantId != null && callerTenantId.equals(question.getTenantId());
    }

    /** Enforces write ownership: Admin may only mutate Vendor-shared content; Host only their own tenant's content. */
    private void enforceOwnership(Question question, JwtPrincipal principal) {
        boolean isHost = actorResolver.isHost(principal);
        if (isHost) {
            UUID callerTenantId = actorResolver.resolveCallerTenantId(principal);
            if (question.getSource() != QuestionSource.HOST
                    || !callerTenantId.equals(question.getTenantId())) {
                throw new ApiException(ErrorCode.ACCESS_DENIED);
            }
        } else {
            if (question.getSource() != QuestionSource.VENDOR) {
                throw new ApiException(ErrorCode.ACCESS_DENIED);
            }
        }
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
