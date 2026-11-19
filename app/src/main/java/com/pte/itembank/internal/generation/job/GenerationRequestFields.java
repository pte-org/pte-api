package com.pte.itembank.internal.generation.job;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.ImageKind;
import com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException;

import java.util.Locale;

/**
 * Parses the request fields shared by a single job and a batch row, so both answer the same codes for the same
 * mistake. Every failure is a {@link GenerationRequestInvalidException} (400).
 */
public final class GenerationRequestFields {

    private GenerationRequestFields() {
    }

    public static CefrLevel cefr(String raw) {
        try {
            CefrLevel level = CefrLevel.parseOrNull(raw);
            if (level != null) {
                return level;
            }
        } catch (IllegalArgumentException unknown) {
            // falls through to the same error as a missing value
        }
        throw invalid(QuestionGenerationConstants.GENERATION_CEFR_INVALID,
                QuestionGenerationConstants.GENERATION_CEFR_INVALID_MESSAGE);
    }

    public static QuestionPool pool(String raw) {
        try {
            return QuestionPool.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw invalid(QuestionGenerationConstants.GENERATION_POOL_INVALID,
                    QuestionGenerationConstants.GENERATION_POOL_INVALID_MESSAGE);
        }
    }

    /**
     * The stored image kind. An explicit PICTURE is refused while AI pictures are turned off; AUTO stays allowed and
     * resolves to a chart in that case ({@link ImageKind#resolve(String, boolean)}).
     */
    public static ImageKind imageKind(String raw, boolean pictureEnabled) {
        ImageKind kind;
        try {
            kind = ImageKind.parse(raw);
        } catch (IllegalArgumentException unknown) {
            throw invalid(QuestionGenerationConstants.GENERATION_IMAGE_KIND_INVALID,
                    QuestionGenerationConstants.GENERATION_IMAGE_KIND_INVALID_MESSAGE);
        }
        if (kind == ImageKind.PICTURE && !pictureEnabled) {
            throw invalid(BatchGenerationConstants.GENERATION_PICTURE_DISABLED,
                    BatchGenerationConstants.GENERATION_PICTURE_DISABLED_MESSAGE);
        }
        return kind;
    }

    public static GenerationRequestInvalidException invalid(String code, String message) {
        return new GenerationRequestInvalidException(code, message);
    }
}
