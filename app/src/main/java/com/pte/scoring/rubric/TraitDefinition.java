package com.pte.scoring.rubric;

import java.util.List;

/** One Pearson trait of a task type: its scale, level descriptions and who scores it. */
public record TraitDefinition(String key, String displayNameVi, String displayNameEn, TraitScale scale,
                              List<LevelDescriptor> levels, TraitSource source) {

    public TraitDefinition {
        levels = List.copyOf(levels);
    }
}
