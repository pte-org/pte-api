package com.pte.itembank.internal.generation.draft;

import com.pte.itembank.internal.generation.pipeline.ContentHasher;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Checks for duplicate content within a job and against existing questions (phase 2 stub).
 * Full DB check will be implemented when repository integration is available.
 */
@Component
public class DuplicateChecker {

    private final Set<String> jobHashes = new HashSet<>();

    /**
     * Check if stimulus text is unique within this job. Database check deferred to phase 3.
     */
    public void checkAndRecordHash(String stimulusText, String taskTypeKey) {
        String hash = ContentHasher.hash(stimulusText);

        if (jobHashes.contains(hash)) {
            throw new GenerationRequestInvalidException(
                    QuestionGenerationConstants.GENERATION_DUPLICATE_CONTENT,
                    "Duplicate content within job: " + taskTypeKey);
        }

        jobHashes.add(hash);
    }

    public void reset() {
        jobHashes.clear();
    }
}
