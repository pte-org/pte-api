package com.pte.itembank.internal.generation.prompt;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.domain.AiPromptStatus;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;
import com.pte.itembank.internal.generation.repository.AiPromptVersionRepository;
import com.pte.itembank.internal.generation.review.ReviewPromptDefaults;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A {@link PromptRegistry} over the shipped seed prompts held in memory, for tests that need real prompt text. */
public final class PromptTestRegistry {

    private PromptTestRegistry() {
    }

    public static PromptContentCodec codec() {
        return new PromptContentCodec(JsonMapper.builder().build());
    }

    public static List<AiPromptVersion> seededVersions() {
        PromptContentCodec codec = codec();
        List<AiPromptVersion> versions = new ArrayList<>();
        try {
            for (Resource resource : new PathMatchingResourcePatternResolver()
                    .getResources(QuestionGenerationConstants.PROMPT_SEED_PATTERN)) {
                String key = resource.getFilename().replace(".json", "");
                AiPromptKind kind = QuestionGenerationConstants.PROMPT_KEY_COMMON_GENERATOR.equals(key)
                        ? AiPromptKind.SHARED : AiPromptKind.GENERATOR;
                PromptContent content = codec.read(new String(resource.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8));
                versions.add(AiPromptVersion.seeded(kind, key, codec.write(content), codec.hash(content),
                        Instant.EPOCH));
            }
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
        PromptContent reviewer = ReviewPromptDefaults.content();
        versions.add(AiPromptVersion.seeded(AiPromptKind.REVIEWER, QuestionGenerationConstants.PROMPT_KEY_REVIEWER,
                codec.write(reviewer), codec.hash(reviewer), Instant.EPOCH));
        return versions;
    }

    public static PromptRegistry create() {
        List<AiPromptVersion> versions = seededVersions();
        AiPromptVersionRepository repository = mock(AiPromptVersionRepository.class);
        when(repository.findByKindAndPromptKeyAndStatus(any(), any(), eq(AiPromptStatus.ACTIVE)))
                .thenAnswer(call -> versions.stream()
                        .filter(version -> version.getKind() == call.getArgument(0)
                                && version.getPromptKey().equals(call.getArgument(1)))
                        .findFirst());
        when(repository.findByKindAndStatus(any(), eq(AiPromptStatus.ACTIVE)))
                .thenAnswer(call -> versions.stream().filter(version -> version.getKind() == call.getArgument(0))
                        .toList());
        return new PromptRegistry(repository, codec());
    }

    public static Optional<AiPromptVersion> find(AiPromptKind kind, String key) {
        return seededVersions().stream()
                .filter(version -> version.getKind() == kind && version.getPromptKey().equals(key)).findFirst();
    }
}
