package com.pte.itembank.internal.generation.llm;

import com.pte.itembank.internal.generation.constant.GenerationLiveConstants;
import com.pte.itembank.internal.generation.draft.AnswerHint;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.live.GenerationLiveContext;
import com.pte.itembank.internal.generation.live.LiveEvent;
import com.pte.itembank.internal.generation.pipeline.GenerationRequest;
import com.pte.itembank.internal.generation.validate.ValidationIssue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Tells the live view each check the writer goes through, so a person sees how a draft was made and tested. */
final class DraftChecks {

    private static final Pattern TOPIC = Pattern.compile("<topic>(.*?)</topic>");
    private static final String TOPIC_SEPARATOR = ", ";

    private DraftChecks() {
    }

    /** The prompt is put together: both prompts, the level, the topic asked for, the topics avoided, any feedback. */
    static void prompt(GenerationRequest request, String systemPrompt, String userPrompt) {
        String hint = request.topicHint() == null ? "" : request.topicHint();
        List<String> avoided = topics(request.avoidTopics());
        boolean feedback = request.feedback() != null && !request.feedback().isBlank();
        List<LiveEvent.CheckDetail> details = new ArrayList<>(List.of(
                DraftMeasures.line(GenerationLiveConstants.DETAIL_SYSTEM_PROMPT, GenerationLiveConstants.UNIT_CHARS,
                        null, systemPrompt.length()),
                DraftMeasures.line(GenerationLiveConstants.DETAIL_TASK_PROMPT, GenerationLiveConstants.UNIT_CHARS,
                        null, userPrompt.length()),
                text(GenerationLiveConstants.DETAIL_PROMPT_KEY, request.promptKey()),
                text(GenerationLiveConstants.DETAIL_CEFR, String.valueOf(request.cefrLevel())),
                text(GenerationLiveConstants.DETAIL_TOPIC, hint)));
        if (!avoided.isEmpty()) {
            details.add(DraftMeasures.line(GenerationLiveConstants.DETAIL_AVOIDED_TOPICS,
                    GenerationLiveConstants.UNIT_TEXT, String.join(TOPIC_SEPARATOR, avoided), avoided.size()));
        }
        if (feedback) {
            details.add(text(GenerationLiveConstants.DETAIL_FEEDBACK, request.feedback()));
        }
        report(GenerationLiveConstants.CHECK_PROMPT, GenerationLiveConstants.CHECK_PASSED, Map.of(
                GenerationLiveConstants.FACT_CEFR, String.valueOf(request.cefrLevel()),
                GenerationLiveConstants.FACT_TOPIC, hint,
                GenerationLiveConstants.FACT_AVOIDED_TOPICS, String.valueOf(avoided.size()),
                GenerationLiveConstants.FACT_FEEDBACK, String.valueOf(feedback)), details);
    }

    static void writing() {
        report(GenerationLiveConstants.CHECK_WRITE, GenerationLiveConstants.CHECK_RUNNING, Map.of(), List.of());
    }

    static void written(String output) {
        report(GenerationLiveConstants.CHECK_WRITE, GenerationLiveConstants.CHECK_PASSED, Map.of(), List.of(
                DraftMeasures.line(GenerationLiveConstants.DETAIL_OUTPUT, GenerationLiveConstants.UNIT_CHARS, null,
                        output == null ? 0 : output.length())));
    }

    /** The JSON was read: every field the model filled in is listed. */
    static void jsonRead(ItemDraft draft, AnswerHint hint) {
        report(GenerationLiveConstants.CHECK_JSON, GenerationLiveConstants.CHECK_PASSED,
                Map.of(GenerationLiveConstants.FACT_RETRY, String.valueOf(false)), DraftMeasures.present(draft, hint));
    }

    /** @param retry the JSON was broken and the model is asked once more */
    static void jsonBroken(boolean retry) {
        report(GenerationLiveConstants.CHECK_JSON, GenerationLiveConstants.CHECK_FAILED,
                Map.of(GenerationLiveConstants.FACT_RETRY, String.valueOf(retry)), List.of());
    }

    /**
     * The task type's rules were applied; the details are the sizes they measured.
     *
     * @param fixed the draft passes after a rewrite asked for by an earlier failure of these rules
     */
    static void rules(ItemDraft draft, List<ValidationIssue> issues, boolean fixed) {
        String broken = issues.stream().map(ValidationIssue::message).distinct().collect(Collectors.joining("\n"));
        report(GenerationLiveConstants.CHECK_RULES,
                issues.isEmpty() ? GenerationLiveConstants.CHECK_PASSED : GenerationLiveConstants.CHECK_FAILED,
                Map.of(GenerationLiveConstants.FACT_ISSUES, broken,
                        GenerationLiveConstants.FACT_FIXED, String.valueOf(fixed)),
                DraftMeasures.measures(draft));
    }

    private static List<String> topics(String avoidTopics) {
        List<String> found = new ArrayList<>();
        if (avoidTopics == null) {
            return found;
        }
        Matcher matcher = TOPIC.matcher(avoidTopics);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static LiveEvent.CheckDetail text(String key, String value) {
        return DraftMeasures.line(key, GenerationLiveConstants.UNIT_TEXT, value, null);
    }

    private static void report(String step, String status, Map<String, String> facts,
                               List<LiveEvent.CheckDetail> details) {
        GenerationLiveContext.check(step, status, facts, details);
    }
}
