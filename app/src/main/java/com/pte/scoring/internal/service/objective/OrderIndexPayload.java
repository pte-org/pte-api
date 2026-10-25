package com.pte.scoring.internal.service.objective;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Parses submitted orderIndex payloads; malformed input never throws, it simply scores as incorrect. */
final class OrderIndexPayload {

    private OrderIndexPayload() {
    }

    /** A missing/malformed/expired-null submission scores incorrect, never throws. */
    static Integer parsePayloadAsOrderIndex(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(payload.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Comma-joined orderIndex set (`MC_READING_MULTIPLE`) — unparsable entries are ignored, never thrown. */
    static Set<Integer> parsePayloadAsOrderIndexSet(String payload) {
        if (payload == null || payload.isBlank()) {
            return Set.of();
        }
        Set<Integer> result = new HashSet<>();
        for (String part : payload.split(",", -1)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                result.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
                // Malformed entry — skip it rather than fail the whole score.
            }
        }
        return result;
    }

    /**
     * Positional orderIndex list (`RE_ORDER_PARAGRAPHS`/fill-blanks) — an
     * empty or unparsable entry becomes {@code null} at that position, never
     * dropped. {@code split(",", -1)} (explicit limit) is required here: Java's
     * default {@code split(",")} silently drops trailing empty strings, which
     * would misparse a payload like {@code "2,0,"} as 2 positions instead of
     * 3 and misalign every gap index after the first empty one.
     */
    static List<Integer> parsePayloadAsOrderIndexList(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (String part : payload.split(",", -1)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                result.add(null);
                continue;
            }
            try {
                result.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ex) {
                result.add(null);
            }
        }
        return result;
    }
}
