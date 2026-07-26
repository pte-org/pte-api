package com.pte.authoring.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.authoring.domain.enums.PteTaskType;
import com.pte.authoring.domain.enums.Skill;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads the versioned task→skill contribution map from
 * {@code config/task-skill-mapping.json} at startup and exposes it read-only.
 * Config-driven (ADR-001) so rubric changes don't require a recompile.
 */
@Component
public class PteTaskTypeSkillMapping {

    private static final String MAPPING_RESOURCE = "config/task-skill-mapping.json";

    private final Map<PteTaskType, Set<Skill>> mapping = new EnumMap<>(PteTaskType.class);

    public PteTaskTypeSkillMapping(ObjectMapper objectMapper) {
        load(objectMapper);
    }

    public Set<Skill> skillsFor(PteTaskType taskType) {
        return mapping.getOrDefault(taskType, Set.of());
    }

    private void load(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(MAPPING_RESOURCE).getInputStream()) {
            JsonNode mappings = objectMapper.readTree(in).path("mappings");
            mappings.fieldNames().forEachRemaining(taskName -> {
                PteTaskType taskType = PteTaskType.valueOf(taskName);
                Set<Skill> skills = new LinkedHashSet<>();
                mappings.get(taskName).forEach(skillNode -> skills.add(Skill.valueOf(skillNode.asText())));
                mapping.put(taskType, skills);
            });
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to load task-skill mapping from " + MAPPING_RESOURCE, ex);
        }
    }
}
