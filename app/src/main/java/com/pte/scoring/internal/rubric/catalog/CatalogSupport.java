package com.pte.scoring.internal.rubric.catalog;

import com.pte.scoring.rubric.LevelDescriptor;
import com.pte.scoring.rubric.TraitDefinition;
import com.pte.scoring.rubric.TraitScale;
import com.pte.scoring.rubric.TraitSource;

import java.util.ArrayList;
import java.util.List;

/** Small builders that keep the per-task catalog files declarative. */
public final class CatalogSupport {

    private CatalogSupport() {
    }

    /** Level descriptions in ascending order: the first text describes level 0, the next level 1, and so on. */
    public static List<LevelDescriptor> levels(String... texts) {
        List<LevelDescriptor> result = new ArrayList<>();
        for (int level = 0; level < texts.length; level++) {
            result.add(new LevelDescriptor(level, texts[level]));
        }
        return result;
    }

    public static TraitDefinition trait(String key, String displayNameVi, String displayNameEn, TraitScale scale,
                                        TraitSource source, String pearsonPages, List<LevelDescriptor> levels) {
        return new TraitDefinition(key, displayNameVi, displayNameEn, scale, levels, source, pearsonPages);
    }
}
