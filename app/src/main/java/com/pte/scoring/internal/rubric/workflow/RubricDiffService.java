package com.pte.scoring.internal.rubric.workflow;

import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.exception.RubricVersionException;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.internal.rubric.workflow.dto.RubricDiffResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Stable leaf-level diff; digest binds the full parameters, catalog, ids and optimistic versions of both sides. */
@Service
public class RubricDiffService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final ScoringRubricVersionRepository versions;

    public RubricDiffService(ScoringRubricVersionRepository versions) { this.versions = versions; }

    @Transactional(readOnly = true)
    public RubricDiffResponse read(UUID candidateId) {
        var candidate = versions.findByPublicId(candidateId).orElseThrow(() ->
                new RubricVersionException(HttpStatus.NOT_FOUND, RubricConstants.RUBRIC_VERSION_NOT_FOUND));
        var active = versions.findByStatus(RubricVersionStatus.ACTIVE).orElseThrow(() ->
                new RubricVersionException(HttpStatus.CONFLICT, RubricConstants.RUBRIC_VERSION_NO_ACTIVE));
        return calculate(candidate, active);
    }

    public RubricDiffResponse calculate(ScoringRubricVersion candidate, ScoringRubricVersion active) {
        Map<String, String> before = parameters(active);
        Map<String, String> after = parameters(candidate);
        Set<String> paths = new TreeSet<>(before.keySet());
        paths.addAll(after.keySet());
        var changes = paths.stream().filter(path -> !Objects.equals(before.get(path), after.get(path)))
                .map(path -> new RubricDiffResponse.Change(path, before.get(path), after.get(path))).toList();
        String material = active.getPublicId() + ":" + active.getLockVersion() + ":" + candidate.getPublicId()
                + ":" + candidate.getLockVersion() + ":" + JSON.writeValueAsString(before) + ":" + JSON.writeValueAsString(after);
        return new RubricDiffResponse(active.getPublicId(), digest(material), changes);
    }

    private static Map<String, String> parameters(ScoringRubricVersion version) {
        Map<String, String> leaves = new TreeMap<>();
        flatten("", JSON.readTree(version.getParameters()), leaves);
        leaves.put("catalogVersion", String.valueOf(version.getCatalogVersion()));
        return leaves;
    }

    private static void flatten(String path, JsonNode node, Map<String, String> leaves) {
        if (node.isObject() && !node.isEmpty()) {
            node.properties().forEach(entry -> flatten(path.isEmpty() ? entry.getKey() : path + "." + entry.getKey(), entry.getValue(), leaves));
        } else if (node.isArray() && !node.isEmpty()) {
            for (int i = 0; i < node.size(); i++) flatten(path + "[" + i + "]", node.get(i), leaves);
        } else leaves.put(path, node.toString());
    }

    private static String digest(String material) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(RubricConstants.RUBRIC_DIFF_UNAVAILABLE, impossible);
        }
    }
}
