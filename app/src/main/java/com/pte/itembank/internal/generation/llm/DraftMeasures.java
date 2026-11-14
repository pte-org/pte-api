package com.pte.itembank.internal.generation.llm;

import com.pte.itembank.internal.generation.constant.GenerationLiveConstants;
import com.pte.itembank.internal.generation.draft.AnswerHint;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.live.LiveEvent;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The fields of a draft as detail lines for the live view: which fields the model wrote, and how long each one is
 * (words of a text, items of a list), which is what the task type's rules measure.
 */
final class DraftMeasures {

    private static final String WHITESPACE = "\\s+";

    private DraftMeasures() {
    }

    /** One line per field the model filled in, then the two answer hints. */
    static List<LiveEvent.CheckDetail> present(ItemDraft draft, AnswerHint hint) {
        List<LiveEvent.CheckDetail> details = new ArrayList<>();
        if (draft == null) {
            return details;
        }
        for (RecordComponent component : draft.getClass().getRecordComponents()) {
            if (filled(valueOf(component, draft))) {
                details.add(line(component.getName(), GenerationLiveConstants.UNIT_PRESENT, null, null));
            }
        }
        if (hint != null) {
            addHint(details, GenerationLiveConstants.DETAIL_HINT_VI, hint.vi());
            addHint(details, GenerationLiveConstants.DETAIL_HINT_EN, hint.en());
        }
        return details;
    }

    /** One line per field with its size: words of a text, items of a list, the value of anything else. */
    static List<LiveEvent.CheckDetail> measures(ItemDraft draft) {
        List<LiveEvent.CheckDetail> details = new ArrayList<>();
        if (draft == null) {
            return details;
        }
        for (RecordComponent component : draft.getClass().getRecordComponents()) {
            Object value = valueOf(component, draft);
            if (!filled(value)) {
                continue;
            }
            String name = component.getName();
            if (value instanceof String text) {
                details.add(line(name, GenerationLiveConstants.UNIT_WORDS, null, words(text)));
            } else if (value instanceof Collection<?> items) {
                details.add(line(name, GenerationLiveConstants.UNIT_ITEMS, null, items.size()));
            } else {
                details.add(line(name, GenerationLiveConstants.UNIT_TEXT, String.valueOf(value), null));
            }
        }
        return details;
    }

    static LiveEvent.CheckDetail line(String key, String unit, String text, Integer count) {
        return new LiveEvent.CheckDetail(key, unit, text, count);
    }

    private static int words(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split(WHITESPACE).length;
    }

    private static void addHint(List<LiveEvent.CheckDetail> details, String key, String text) {
        if (text != null && !text.isBlank()) {
            details.add(line(key, GenerationLiveConstants.UNIT_WORDS, null, words(text)));
        }
    }

    private static boolean filled(Object value) {
        if (value instanceof String text) {
            return !text.isBlank();
        }
        if (value instanceof Collection<?> items) {
            return !items.isEmpty();
        }
        return value != null;
    }

    /** A field that cannot be read is left out: the details only describe the draft, they never fail it. */
    private static Object valueOf(RecordComponent component, ItemDraft draft) {
        try {
            return component.getAccessor().invoke(draft);
        } catch (ReflectiveOperationException unreadable) {
            return null;
        }
    }
}
