package com.pte.shared.audit;

import com.pte.shared.audit.internal.constant.AuditLogConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a module's business snapshot into the JSON stored in {@code audit_logs.before_state/after_state}.
 * Record properties and map keys are sorted so two snapshots of the same state produce identical text and a
 * web diff stays quiet.
 * A failure never breaks the business command: the state is dropped and the summary row is still written.
 */
final class AuditStateSerializer {

    private static final Logger LOG = LoggerFactory.getLogger(AuditStateSerializer.class);
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            // Record components are creator properties, which Jackson otherwise keeps first in declaration order.
            .disable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private AuditStateSerializer() {
    }

    /** @return the JSON text, a truncation marker when it is too large, or null for a null or unserializable state */
    static String toJson(Object state) {
        if (state == null) {
            return null;
        }
        String json;
        try {
            json = MAPPER.writeValueAsString(state);
        } catch (JacksonException exception) {
            LOG.warn("Audit state of type {} could not be serialized; storing the summary only",
                    state.getClass().getSimpleName());
            return null;
        }
        return json.length() <= AuditLogConstants.STATE_MAX_CHARS ? json : truncated(json);
    }

    private static String truncated(String json) {
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put(AuditLogConstants.STATE_TRUNCATED_KEY, true);
        marker.put(AuditLogConstants.STATE_PREVIEW_KEY, json.substring(0, AuditLogConstants.STATE_PREVIEW_CHARS));
        return MAPPER.writeValueAsString(marker);
    }
}
