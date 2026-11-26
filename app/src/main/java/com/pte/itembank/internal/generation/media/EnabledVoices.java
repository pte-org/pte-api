package com.pte.itembank.internal.generation.media;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The catalog voices the deployment has not switched off ({@code QUESTION_GEN_VOICES_DISABLED}). */
@Component
public class EnabledVoices {

    private static final Logger LOG = LoggerFactory.getLogger(EnabledVoices.class);
    /** Settings of the earlier design; set on a server that was deployed before the catalog existed. */
    private static final List<String> RETIRED_SETTINGS = List.of("QUESTION_GEN_VOICES_US", "QUESTION_GEN_VOICES_UK");

    private final List<KokoroVoiceCatalog.Voice> enabled;

    public EnabledVoices(QuestionGenerationProperties properties, Environment environment) {
        Set<String> disabled = parse(properties.getKokoro().getVoicesDisabled());
        disabled.stream()
                .filter(code -> KokoroVoiceCatalog.find(code).isEmpty())
                .forEach(code -> LOG.warn("Ignoring a disabled voice that is not in the catalog: voice={}", code));
        this.enabled = KokoroVoiceCatalog.all().stream()
                .filter(voice -> !disabled.contains(voice.code()))
                .toList();
        if (enabled.isEmpty()) {
            throw new IllegalStateException(QuestionGenerationConstants.CONFIG_ALL_VOICES_DISABLED);
        }
        for (KokoroVoiceCatalog.Accent accent : KokoroVoiceCatalog.Accent.values()) {
            if (of(accent).isEmpty()) {
                LOG.warn("Every voice of one accent is disabled, the other accent is used instead: accent={}", accent);
            }
        }
        RETIRED_SETTINGS.stream()
                .filter(name -> environment.getProperty(name) != null)
                .forEach(name -> LOG.warn("{} is no longer used; switch voices off with QUESTION_GEN_VOICES_DISABLED",
                        name));
    }

    public List<KokoroVoiceCatalog.Voice> all() {
        return enabled;
    }

    public List<KokoroVoiceCatalog.Voice> of(KokoroVoiceCatalog.Accent accent) {
        return enabled.stream().filter(voice -> voice.accent() == accent).toList();
    }

    public Optional<KokoroVoiceCatalog.Voice> find(String code) {
        return enabled.stream().filter(voice -> voice.code().equals(code)).findFirst();
    }

    private static Set<String> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        Arrays.stream(csv.split(",")).map(String::trim).filter(code -> !code.isEmpty()).forEach(codes::add);
        return codes;
    }
}
