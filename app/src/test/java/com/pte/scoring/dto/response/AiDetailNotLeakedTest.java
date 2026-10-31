package com.pte.scoring.dto.response;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The AI breakdown (criteria, feedback, transcript, raw provider detail) is for the host and the assigned
 * examiner. Responses that reach students, reports or the blind examiner work contract must not carry it.
 */
class AiDetailNotLeakedTest {

    private static final Set<String> AI_DETAIL_FIELDS = Set.of("ai", "criteria", "feedback", "transcript",
            "detail", "fallback", "aiFallback", "failureReason", "aiFailureReason");

    @ParameterizedTest
    @ValueSource(strings = {
            "com.pte.reporting.internal.dto.response.ReportResponse",
            "com.pte.reporting.internal.dto.response.SkillScoreResponse",
            "com.pte.reporting.internal.dto.response.ReportPublicationSummaryResponse",
            "com.pte.scoring.dto.response.ReportScoringAnswerView",
            "com.pte.scoring.dto.response.ReportPublicationScoringView",
            "com.pte.scoring.dto.response.ExaminerScoringWorkItemView",
            "com.pte.scoring.dto.response.ExaminerQueueItemResponse",
            "com.pte.attempt.dto.response.AttemptSummaryView"})
    void studentReportAndBlindWorkResponsesCarryNoAiDetail(String className) throws ClassNotFoundException {
        List<String> leaks = new ArrayList<>();
        collectLeaks(Class.forName(className), new HashSet<>(), leaks);

        assertThat(leaks).as("AI detail fields reachable from %s", className).isEmpty();
    }

    private void collectLeaks(Class<?> type, Set<Class<?>> seen, List<String> leaks) {
        if (!type.isRecord() || !type.getPackageName().startsWith("com.pte") || !seen.add(type)) {
            return;
        }
        for (RecordComponent component : type.getRecordComponents()) {
            if (AI_DETAIL_FIELDS.contains(component.getName())
                    || component.getType() == AiScoreDetailResponse.class) {
                leaks.add(type.getSimpleName() + "." + component.getName());
            }
            collectLeaks(component.getType(), seen, leaks);
            collectGenericLeaks(component.getGenericType(), seen, leaks);
        }
    }

    private void collectGenericLeaks(Type type, Set<Class<?>> seen, List<String> leaks) {
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                if (argument instanceof Class<?> argumentClass) {
                    collectLeaks(argumentClass, seen, leaks);
                } else {
                    collectGenericLeaks(argument, seen, leaks);
                }
            }
        }
    }
}
