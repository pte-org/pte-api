package com.pte.assessment.internal.service.assembly;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The questions a centre has already used in earlier exams, which a new exam prefers to avoid. The
 * preference is soft: a task type that has too few unused questions draws from its whole pool, so an
 * exam can always be built when the bank is large enough for it alone.
 */
public final class FreshQuestions {

    private static final FreshQuestions NONE = new FreshQuestions(null);

    private final Set<UUID> used;

    private FreshQuestions(Set<UUID> used) {
        this.used = used;
    }

    /** No preference: every question is as good as any other. */
    public static FreshQuestions none() {
        return NONE;
    }

    public static FreshQuestions avoiding(Set<UUID> used) {
        return new FreshQuestions(Set.copyOf(used));
    }

    public boolean isActive() {
        return used != null;
    }

    /** The pool without the used questions when that still leaves {@code needed}; the whole pool otherwise. */
    public <T> List<T> prefer(List<T> pool, Function<T, UUID> idOf, int needed) {
        if (used == null) {
            return pool;
        }
        List<T> fresh = pool.stream().filter(candidate -> !used.contains(idOf.apply(candidate))).toList();
        return fresh.size() >= needed ? fresh : pool;
    }

    /** How many of the picked questions the centre has used before; null when no preference was asked for. */
    public Integer repeatedIn(Collection<UUID> picked) {
        return used == null ? null : (int) picked.stream().filter(used::contains).count();
    }
}
