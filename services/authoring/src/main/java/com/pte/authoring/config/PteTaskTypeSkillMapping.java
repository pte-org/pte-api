package com.pte.authoring.config;

import com.pte.authoring.domain.enums.PteTaskType;
import com.pte.authoring.domain.enums.Skill;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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

    public PteTaskTypeSkillMapping(JsonMapper jsonMapper) {
        load(jsonMapper);
    }

    public Set<Skill> skillsFor(PteTaskType taskType) {
        return mapping.getOrDefault(taskType, Set.of());
    }

    private void load(JsonMapper jsonMapper) {
        try (InputStream in = new ClassPathResource(MAPPING_RESOURCE).getInputStream()) {
            JsonNode mappings = jsonMapper.readTree(in).path("mappings");
            mappings.propertyNames().forEach(taskName -> {
                PteTaskType taskType = PteTaskType.valueOf(taskName);
                Set<Skill> skills = new LinkedHashSet<>();
                mappings.get(taskName).forEach(skillNode -> skills.add(Skill.valueOf(skillNode.asText())));
                mapping.put(taskType, skills);
            });
        } catch (IOException | JacksonException | IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to load task-skill mapping from " + MAPPING_RESOURCE, ex);
        }
    }
}
