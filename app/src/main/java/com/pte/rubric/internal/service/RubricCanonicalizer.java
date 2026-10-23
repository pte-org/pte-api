package com.pte.rubric.internal.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Produces the deterministic UTF-8 SHA-256 hash used by rubric provenance. */
@Component
public class RubricCanonicalizer {

    private final ObjectMapper objectMapper;

    public RubricCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String canonicalize(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(normalize(node));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not canonicalize rubric document", ex);
        }
    }

    public String sha256(String canonicalJson) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public String runtimeCompatibilityHash(JsonNode document) {
        JsonNode runtime = document == null ? null : document.get("runtime");
        if (runtime == null || !runtime.isObject()) {
            return null;
        }
        ObjectNode selected = objectMapper.createObjectNode();
        for (String field : List.of("profileKey", "profileVersion", "screenKey", "behaviorKey",
                "rendererKey", "answerSchemaVersion", "scoringProfileKey", "scoringProfileVersion",
                "contractVersion", "scoringMode", "minSupportedAppVersion",
                "authoringContractKey", "authoringContractVersion", "requiredClientCapabilities")) {
            JsonNode value = runtime.get(field);
            if (value != null && !value.isNull()) {
                selected.set(field, value);
            }
        }
        return sha256(canonicalize(selected));
    }

    public String runtimeCompatibilityHash(TaskRuntimeProfileDescriptor profile) {
        ObjectNode selected = objectMapper.createObjectNode();
        selected.put("profileKey", profile.profileKey());
        selected.put("profileVersion", profile.profileVersion());
        selected.put("screenKey", profile.screenKey());
        selected.put("behaviorKey", profile.behaviorKey());
        selected.put("rendererKey", profile.rendererKey());
        selected.put("answerSchemaVersion", profile.answerSchemaVersion());
        selected.put("scoringProfileKey", profile.scoringProfileKey());
        selected.put("scoringProfileVersion", profile.scoringProfileVersion());
        selected.put("contractVersion", profile.contractVersion());
        selected.put("scoringMode", profile.scoringMode());
        selected.put("minSupportedAppVersion", profile.minSupportedAppVersion());
        selected.put("authoringContractKey", profile.authoringContractKey());
        selected.put("authoringContractVersion", profile.authoringContractVersion());
        selected.set("requiredClientCapabilities", objectMapper.valueToTree(profile.requiredClientCapabilities()));
        return sha256(canonicalize(selected));
    }

    private JsonNode normalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            node.propertyNames().forEach(names::add);
            names.sort(Comparator.naturalOrder());
            for (String name : names) {
                JsonNode value = node.get(name);
                if (value != null && !value.isNull()) {
                    result.set(name, normalize(value));
                }
            }
            return result;
        }
        if (node.isArray()) {
            var result = objectMapper.createArrayNode();
            node.forEach(item -> result.add(normalize(item)));
            return result;
        }
        if (node.isTextual()) {
            return objectMapper.getNodeFactory().textNode(
                    Normalizer.normalize(node.asText().trim().replace("\r\n", "\n").replace('\r', '\n'),
                            Normalizer.Form.NFC));
        }
        return node;
    }
}
