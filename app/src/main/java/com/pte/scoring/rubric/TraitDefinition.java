package com.pte.scoring.rubric;

import java.util.List;

/** One Pearson trait of a task type: its scale, level descriptions, who scores it and where the Score Guide says so. */
public record TraitDefinition(String key, String displayNameVi, String displayNameEn, TraitScale scale,
                              List<LevelDescriptor> levels, TraitSource source, String pearsonPages) {

    public TraitDefinition {
        levels = List.copyOf(levels);
    }
}
