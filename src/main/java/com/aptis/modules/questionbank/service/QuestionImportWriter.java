package com.aptis.modules.questionbank.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.modules.asset.domain.AssetType;
import com.aptis.modules.asset.dto.request.CreateAssetRequest;
import com.aptis.modules.asset.dto.response.AssetResponse;
import com.aptis.modules.asset.interfaces.AssetOperations;
import com.aptis.modules.questionbank.constant.QuestionBankApiConstants;
import com.aptis.modules.questionbank.domain.DifficultyLevel;
import com.aptis.modules.questionbank.domain.Question;
import com.aptis.modules.questionbank.domain.QuestionSource;
import com.aptis.modules.questionbank.domain.QuestionStatus;
import com.aptis.modules.questionbank.domain.QuestionType;
import com.aptis.modules.questionbank.repository.QuestionRepository;
import com.aptis.modules.storage.dto.response.UploadResponse;

/**
 * Owns the all-or-nothing DB write for bulk import. Kept as its own bean (rather than a
 * private method on {@link QuestionImportService}) so its {@link Transactional} boundary
 * is honored by Spring's proxy — a self-invoked private method would silently run
 * non-transactionally. Audio uploads happen before this is called; this method only
 * persists rows that are already fully validated and (if applicable) already uploaded.
 * Asset records go through the shared {@link AssetOperations#createAsset} — its own
 * default REQUIRED propagation joins this method's already-open transaction rather than
 * starting a separate one, so Question + Asset rows still commit or roll back together.
 */
@Service
public class QuestionImportWriter {

    private final QuestionRepository questionRepository;
    private final AssetOperations assetOperations;

    public QuestionImportWriter(QuestionRepository questionRepository, AssetOperations assetOperations) {
        this.questionRepository = questionRepository;
        this.assetOperations = assetOperations;
    }

    @Transactional
    public int writeAll(
            List<ParsedQuestionImportRow> rows,
            Map<Integer, UploadResponse> audioUploadsByRow,
            UUID tenantId,
            UUID createdBy) {

        for (ParsedQuestionImportRow row : rows) {
            UUID audioAssetId = null;
            UploadResponse upload = audioUploadsByRow.get(row.rowNumber());
            if (upload != null) {
                AssetResponse asset = assetOperations.createAsset(new CreateAssetRequest(
                        AssetType.AUDIO_QUESTION,
                        upload.storageKey(),
                        upload.cdnUrl(),
                        upload.filename(),
                        upload.sizeBytes(),
                        upload.mimeType(),
                        createdBy,
                        tenantId,
                        null));
                audioAssetId = asset.publicId();
            }

            Question question = new Question();
            question.setSkill(row.skill());
            question.setPart(1);
            question.setQuestionType(QuestionType.MULTIPLE_CHOICE);
            question.setContent(row.content());
            question.setOptions(row.options());
            question.setCorrectAnswers(List.of(row.correctAnswer()));
            question.setDifficultyLevel(DifficultyLevel.valueOf(QuestionBankApiConstants.IMPORT_DEFAULT_DIFFICULTY));
            question.setAudioAssetId(audioAssetId);
            question.setCreatedBy(createdBy);
            question.setVersion(1);
            question.setIsCurrent(true);
            question.setIsImmutable(false);
            question.setStatus(QuestionStatus.DRAFT);
            question.setSource(QuestionSource.HOST);
            question.setTenantId(tenantId);

            questionRepository.save(question);
        }

        return rows.size();
    }
}
