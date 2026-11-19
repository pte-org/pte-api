package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.batch.dto.SubmitGenerationBatchRequest;
import com.pte.itembank.internal.generation.config.GenerationLimits;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.BatchRow;
import com.pte.itembank.internal.generation.exception.GenerationDisabledException;
import com.pte.itembank.internal.generation.job.GenerationAccessPolicy;
import com.pte.itembank.internal.generation.job.GenerationRequestFields;
import com.pte.itembank.internal.generation.job.GenerationTaskTypeCatalog;
import com.pte.itembank.internal.generation.job.TopicHintPolicy;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Checks a batch request before the submit lock is taken: flags, role, row shape, catalog, limits and topics. The
 * controller's Bean Validation covers the shape for HTTP callers; it is checked again here because the service must
 * hold its own rules (null rows or lists, a total that overflows int).
 */
@Component
public class BatchRequestValidator {

    /** The request after validation: parsed values, normalized topics, total questions. */
    public record ValidatedBatch(QuestionPool pool, List<BatchRow> rows, int total) {
    }

    private final GenerationTaskTypeCatalog catalog;
    private final QuestionGenerationProperties properties;
    private final GenerationLimits limits;

    public BatchRequestValidator(GenerationTaskTypeCatalog catalog, QuestionGenerationProperties properties,
                                 GenerationLimits limits) {
        this.catalog = catalog;
        this.properties = properties;
        this.limits = limits;
    }

    public ValidatedBatch validate(SubmitGenerationBatchRequest request, CurrentUser caller) {
        GenerationAccessPolicy.requireAuthor(caller);
        if (!properties.isEnabled()) {
            throw new GenerationDisabledException();
        }
        if (!properties.isBatchEnabled()) {
            throw new GenerationDisabledException(BatchGenerationConstants.GENERATION_BATCH_DISABLED,
                    BatchGenerationConstants.GENERATION_BATCH_DISABLED_MESSAGE);
        }
        List<SubmitGenerationBatchRequest.Row> raw = request.rows();
        if (raw == null || raw.isEmpty() || raw.size() > BatchGenerationConstants.MAX_ROWS_PER_BATCH
                || raw.stream().anyMatch(Objects::isNull)) {
            throw GenerationRequestFields.invalid(BatchGenerationConstants.GENERATION_BATCH_ROWS_OUT_OF_RANGE,
                    BatchGenerationConstants.GENERATION_BATCH_ROWS_OUT_OF_RANGE_MESSAGE);
        }
        QuestionPool pool = GenerationRequestFields.pool(request.pool());
        int maxPerBatch = limits.effectiveMaxItemsPerBatch();
        long total = 0;
        List<BatchRow> rows = new ArrayList<>();
        for (SubmitGenerationBatchRequest.Row row : raw) {
            BatchRow parsed = row(row, maxPerBatch);
            total += parsed.count();
            rows.add(parsed);
        }
        if (total > maxPerBatch) {
            throw sizeOutOfRange();
        }
        return new ValidatedBatch(pool, List.copyOf(rows), (int) total);
    }

    private BatchRow row(SubmitGenerationBatchRequest.Row row, int maxPerBatch) {
        String taskType = catalog.find(row.taskType()).orElseThrow(() -> GenerationRequestFields.invalid(
                QuestionGenerationConstants.GENERATION_TASK_TYPE_UNSUPPORTED,
                QuestionGenerationConstants.GENERATION_TASK_TYPE_UNSUPPORTED_MESSAGE)).name();
        if (row.count() == null || row.count() < 1 || row.count() > maxPerBatch) {
            throw sizeOutOfRange();
        }
        return new BatchRow(taskType, GenerationRequestFields.cefr(row.cefrLevel()), row.count(),
                topics(row.topicHints()), GenerationRequestFields.imageKind(row.imageKind(),
                properties.isPictureEnabled()));
    }

    /** Normalized topics; blanks and duplicates dropped. A hostile topic is refused, not silently cleaned. */
    private static List<String> topics(List<String> hints) {
        if (hints == null) {
            return List.of();
        }
        if (hints.size() > BatchGenerationConstants.MAX_TOPIC_HINTS_PER_ROW) {
            throw GenerationRequestFields.invalid(BatchGenerationConstants.GENERATION_BATCH_TOPIC_HINTS_OUT_OF_RANGE,
                    BatchGenerationConstants.GENERATION_BATCH_TOPIC_HINTS_OUT_OF_RANGE_MESSAGE);
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String hint : hints) {
            String normalized = TopicHintPolicy.normalize(hint);
            if (normalized != null) {
                unique.add(normalized);
            }
        }
        return List.copyOf(unique);
    }

    private static RuntimeException sizeOutOfRange() {
        return GenerationRequestFields.invalid(BatchGenerationConstants.GENERATION_BATCH_SIZE_OUT_OF_RANGE,
                BatchGenerationConstants.GENERATION_BATCH_SIZE_OUT_OF_RANGE_MESSAGE);
    }
}
