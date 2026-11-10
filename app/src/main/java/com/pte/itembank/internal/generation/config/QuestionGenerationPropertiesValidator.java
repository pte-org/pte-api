package com.pte.itembank.internal.generation.config;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;

/**
 * Fails the application start on unsafe or incomplete generation settings. Messages name the property, never its
 * value, so a bad secret cannot leak through the start-up log.
 */
public class QuestionGenerationPropertiesValidator implements InitializingBean {

    private static final Profiles LOCAL_PROFILES = Profiles.of("test", "local", "local-schema");
    private static final Profiles PRODUCTION_PROFILES = Profiles.of("prod", "production");
    private static final String HTTPS = "https";
    private static final String HTTP = "http";

    private final QuestionGenerationProperties properties;
    private final Environment environment;

    public QuestionGenerationPropertiesValidator(QuestionGenerationProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        boolean local = environment.acceptsProfiles(LOCAL_PROFILES);
        if (properties.isStub() && (!local || environment.acceptsProfiles(PRODUCTION_PROFILES))) {
            throw new IllegalStateException(QuestionGenerationConstants.CONFIG_STUB_PROFILE_ONLY);
        }
        if (!properties.isEnabled() || properties.isStub()) {
            return;
        }
        QuestionGenerationProperties.Llm llm = properties.getLlm();
        require(llm.getBaseUrl(), QuestionGenerationConstants.CONFIG_LLM_BASE_URL_REQUIRED);
        require(llm.getApiKey(), QuestionGenerationConstants.CONFIG_LLM_API_KEY_REQUIRED);
        require(llm.getGeneratorModel(), QuestionGenerationConstants.CONFIG_LLM_MODEL_REQUIRED);
        require(llm.getReviewerModel(), QuestionGenerationConstants.CONFIG_LLM_MODEL_REQUIRED);
        if (!isAllowedUrl(llm.getBaseUrl(), local)) {
            throw new IllegalStateException(QuestionGenerationConstants.CONFIG_LLM_BASE_URL_INVALID);
        }
    }

    private static void require(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
    }

    private static boolean isAllowedUrl(String value, boolean local) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean hasHost = StringUtils.hasText(uri.getHost());
            return hasHost && (HTTPS.equals(scheme) || (local && HTTP.equals(scheme)));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
