package com.pte.scoring.internal.vendor;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.internal.vendor.gemini.scorer.SummarizeSpokenTextScorer;
import com.pte.scoring.internal.vendor.gemini.scorer.SummarizeWrittenTextScorer;
import com.pte.scoring.internal.vendor.gemini.scorer.WriteEssayScorer;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.TaskRubric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decision B of the rubric plan (phase 4), checked statically: every {@link AiAnswerScorer} bean exists only when
 * {@code scoring.ai.provider=azure-gemini}. Under {@code stub} or {@code openai-compatible} there is therefore no
 * scorer for a rubric task type, and the worker and practice routers fail it with
 * {@code AI_RUBRIC_SCORER_UNAVAILABLE} instead of asking the generic client (see AiScoringWorkerRubricProviderTest
 * and PracticeRubricProviderTest).
 */
class RubricScorerProviderGateTest {

    private static final String PROVIDER_PROPERTY = "scoring.ai.provider";
    private static final String RUBRIC_PROVIDER = "azure-gemini";
    private static final String SCAN_ROOT = "com.pte.scoring";

    @Test
    void everyAnswerScorerBeanIsGatedOnTheAzureGeminiProvider() throws ClassNotFoundException {
        List<Class<?>> scorers = scorerComponents(RUBRIC_PROVIDER);

        assertThat(scorers).contains(WriteEssayScorer.class, SummarizeWrittenTextScorer.class,
                SummarizeSpokenTextScorer.class);
        for (Class<?> scorer : scorers) {
            ConditionalOnProperty condition = scorer.getAnnotation(ConditionalOnProperty.class);
            assertThat(condition).as(scorer.getSimpleName()).isNotNull();
            assertThat(List.of(condition.name())).as(scorer.getSimpleName()).containsExactly(PROVIDER_PROPERTY);
            assertThat(condition.havingValue()).as(scorer.getSimpleName()).isEqualTo(RUBRIC_PROVIDER);
            assertThat(condition.matchIfMissing()).as(scorer.getSimpleName()).isFalse();
        }
    }

    /** The component scan Spring itself would run: under these providers not one scorer bean is registered. */
    @ParameterizedTest
    @ValueSource(strings = {"stub", "openai-compatible"})
    void providersWithoutRubricScorersRegisterNoAnswerScorer(String provider) throws ClassNotFoundException {
        assertThat(scorerComponents(provider)).isEmpty();
    }

    @Test
    void anUnsetProviderRegistersNoAnswerScorer() throws ClassNotFoundException {
        assertThat(scorerComponents(null)).isEmpty();
    }

    /** Every catalog task type with traits is rubric-scored (writing in phase 3, speaking in phase 5), no other. */
    @Test
    void theRubricScoredTaskTypesAreExactlyTheCatalogTasksWithTraits() {
        RubricCatalog catalog = new RubricCatalogImpl();

        assertThat(ScoringConstants.RUBRIC_SCORED_TASK_TYPES).containsExactlyInAnyOrderElementsOf(catalog.all()
                .stream().filter(task -> !task.isObjective()).map(TaskRubric::taskType).toList());
    }

    @Test
    void everyRubricScoredTaskTypeHasAScorerBeanUnderAzureGemini() throws ClassNotFoundException {
        List<String> handled = new ArrayList<>();
        for (Class<?> scorer : scorerComponents(RUBRIC_PROVIDER)) {
            handled.add(scorer.getSimpleName());
        }

        assertThat(handled).contains("ReadAloudScorer", "RepeatSentenceScorer", "ShortQuestionScorer",
                "DescribeImageScorer", "RetellLectureScorer", "RespondToSituationScorer",
                "SummarizeGroupDiscussionScorer", "WriteEssayScorer", "SummarizeWrittenTextScorer",
                "SummarizeSpokenTextScorer");
    }

    /** Scans with the given provider (null = unset), so {@code @ConditionalOnProperty} is evaluated as at startup. */
    private static List<Class<?>> scorerComponents(String provider) throws ClassNotFoundException {
        MockEnvironment environment = new MockEnvironment();
        if (provider != null) {
            environment.setProperty(PROVIDER_PROPERTY, provider);
        }
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false, environment);
        scanner.addIncludeFilter(new AssignableTypeFilter(AiAnswerScorer.class));
        Set<BeanDefinition> candidates = scanner.findCandidateComponents(SCAN_ROOT);
        List<Class<?>> components = new ArrayList<>();
        for (BeanDefinition candidate : candidates) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            if (type.isAnnotationPresent(Component.class)) {
                components.add(type);
            }
        }
        return components;
    }
}
