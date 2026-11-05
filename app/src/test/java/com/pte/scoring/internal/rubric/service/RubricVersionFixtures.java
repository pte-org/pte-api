package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.exception.RubricVersionException;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.RubricParameters;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitScale;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Shared builders for the rubric-version tests: catalog defaults as stored JSON, JSON edits, version rows. */
public final class RubricVersionFixtures {

    public static final RubricCatalog CATALOG = new RubricCatalogImpl();
    public static final JsonMapper JSON = JsonMapper.builder().build();
    public static final RubricParametersCodec CODEC = new RubricParametersCodec(JSON);
    public static final String V32_RESOURCE = "db/migration/V32__scoring_rubric_versions.sql";
    private static final Pattern SEED_JSON = Pattern.compile("\\$rubric_v1\\$\\s*(\\{.*?})\\$rubric_v1\\$",
            Pattern.DOTALL);

    private RubricVersionFixtures() {
    }

    /**
     * The catalog defaults of every task type with Azure mapping table 1 and the catalog's trait scales, i.e. what
     * version 1 must hold.
     */
    public static StoredRubricParameters defaults(RubricCatalog catalog) {
        Map<String, RubricParameters> tasks = new LinkedHashMap<>();
        Map<String, Map<String, TraitScale>> scales = new LinkedHashMap<>();
        for (TaskRubric task : catalog.all()) {
            RubricParameters base = catalog.defaultParameters(task.taskType());
            tasks.put(task.taskType(), new RubricParameters(base.traitWeights(), base.formBands(),
                    base.gateEnabled(), String.valueOf(RubricConstants.AZURE_BAND_MAPPING_VERSION_1)));
            scales.put(task.taskType(), StoredRubricParameters.scalesOf(task));
        }
        return new StoredRubricParameters(RubricConstants.AZURE_BAND_MAPPING_VERSION_1, tasks, scales);
    }

    public static String defaultsJson() {
        return CODEC.encode(defaults(CATALOG));
    }

    /** Defaults JSON with the root object edited. */
    public static String editRoot(Consumer<ObjectNode> edit) {
        ObjectNode root = (ObjectNode) JSON.readTree(defaultsJson());
        edit.accept(root);
        return JSON.writeValueAsString(root);
    }

    /** Defaults JSON with one task type's entry edited. */
    public static String editTask(String taskType, Consumer<ObjectNode> edit) {
        return editRoot(root -> edit.accept((ObjectNode) root.get(RubricConstants.PARAM_TASK_TYPES).get(taskType)));
    }

    public static ObjectNode child(ObjectNode task, String key) {
        return (ObjectNode) task.get(key);
    }

    public static ScoringRubricVersion version(RubricVersionStatus status, String parametersJson) {
        ScoringRubricVersion version = ScoringRubricVersion.draft(1, parametersJson, RubricConstants.CATALOG_VERSION,
                UUID.randomUUID());
        version.setPublicId(UUID.randomUUID());
        ReflectionTestUtils.setField(version, "status", status);
        return version;
    }

    public static String v32Sql() {
        try (InputStream in = RubricVersionFixtures.class.getClassLoader().getResourceAsStream(V32_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(V32_RESOURCE + " not on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The version-1 parameters exactly as written between the $rubric_v1$ dollar quotes of V32. */
    public static String seedJson() {
        Matcher matcher = SEED_JSON.matcher(v32Sql());
        if (!matcher.find()) {
            throw new IllegalStateException("V32 has no $rubric_v1$ seed block");
        }
        return matcher.group(1);
    }

    public static void assertRubricVersionError(ThrowingCallable call, HttpStatus status, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(RubricVersionException.class, error -> {
            assertThat(error.getCode()).isEqualTo(code);
            assertThat(error.getStatus()).isEqualTo(status);
        });
    }
}
