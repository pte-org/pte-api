package com.pte.reporting.config;

import com.pte.reporting.domain.enums.Skill;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads the versioned task-type → skill contribution map from
 * {@code config/task-skill-mapping.json} — reporting's own copy of authoring's
 * config (ADR-001: config is data, not runtime state).
 */
@Component
public class TaskSkillMappingConfig {

    private static final String RESOURCE = "config/task-skill-mapping.json";

    private final Map<String, Set<Skill>> mapping = new HashMap<>();

    public TaskSkillMappingConfig(JsonMapper jsonMapper) {
        load(jsonMapper);
    }

    public Set<Skill> skillsFor(String taskType) {
        return mapping.getOrDefault(taskType, Set.of());
    }

    private void load(JsonMapper jsonMapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            JsonNode mappings = jsonMapper.readTree(in).path("mappings");
            mappings.propertyNames().forEach(taskType -> {
                Set<Skill> skills = new LinkedHashSet<>();
                mappings.get(taskType).forEach(skillNode -> skills.add(Skill.valueOf(skillNode.asText())));
                mapping.put(taskType, skills);
            });
        } catch (IOException | JacksonException | IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to load task-skill mapping from " + RESOURCE, ex);
        }
    }
}
