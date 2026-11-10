package com.pte.itembank.internal.generation.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionGenerationPropertiesValidatorTest {

    private static final String KEY = "very-private-key-value";

    private static QuestionGenerationProperties enabledWithKey(String baseUrl) {
        QuestionGenerationProperties properties = new QuestionGenerationProperties();
        properties.setEnabled(true);
        properties.getLlm().setBaseUrl(baseUrl);
        properties.getLlm().setApiKey(KEY);
        return properties;
    }

    private static void validate(QuestionGenerationProperties properties, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        new QuestionGenerationPropertiesValidator(properties, environment).afterPropertiesSet();
    }

    @Test
    void aDisabledFeatureNeedsNoConfiguration() {
        assertThatCode(() -> validate(new QuestionGenerationProperties(), "prod")).doesNotThrowAnyException();
    }

    @Test
    void theStubIsRefusedOutsideTheLocalProfiles() {
        QuestionGenerationProperties properties = new QuestionGenerationProperties();
        properties.setStub(true);

        assertThatThrownBy(() -> validate(properties, "prod")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> validate(properties)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theStubIsAllowedInTheTestLocalAndLocalSchemaProfiles() {
        QuestionGenerationProperties properties = new QuestionGenerationProperties();
        properties.setStub(true);
        properties.setEnabled(true);

        assertThatCode(() -> validate(properties, "test")).doesNotThrowAnyException();
        assertThatCode(() -> validate(properties, "local")).doesNotThrowAnyException();
        assertThatCode(() -> validate(properties, "local-schema")).doesNotThrowAnyException();
    }

    @Test
    void theStubIsRefusedWhenAProductionProfileIsMixedWithALocalOne() {
        QuestionGenerationProperties properties = new QuestionGenerationProperties();
        properties.setStub(true);

        assertThatThrownBy(() -> validate(properties, "local", "prod")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anEnabledRealProviderNeedsAUrlAKeyAndModels() {
        assertThatThrownBy(() -> validate(enabledWithKey(""), "prod")).isInstanceOf(IllegalStateException.class);
        QuestionGenerationProperties noKey = enabledWithKey("https://router.example/v1");
        noKey.getLlm().setApiKey(" ");
        assertThatThrownBy(() -> validate(noKey, "prod")).isInstanceOf(IllegalStateException.class);
        QuestionGenerationProperties noModel = enabledWithKey("https://router.example/v1");
        noModel.getLlm().setReviewerModel("");
        assertThatThrownBy(() -> validate(noModel, "prod")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void plainHttpIsOnlyAcceptedForLocalProfiles() {
        QuestionGenerationProperties properties = enabledWithKey("http://router.example/v1");

        assertThatThrownBy(() -> validate(properties, "prod")).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> validate(properties, "local")).doesNotThrowAnyException();
        assertThatThrownBy(() -> validate(enabledWithKey("ftp://router.example/v1"), "local"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> validate(enabledWithKey("not a url"), "local"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anHttpsUrlWithAKeyIsAccepted() {
        assertThatCode(() -> validate(enabledWithKey("https://router.example/v1"), "prod")).doesNotThrowAnyException();
    }

    @Test
    void messagesNeverContainTheConfiguredKeyOrUrl() {
        QuestionGenerationProperties properties = enabledWithKey("http://secret-host.example/v1");

        assertThatThrownBy(() -> validate(properties, "prod"))
                .satisfies(failure -> {
                    org.assertj.core.api.Assertions.assertThat(failure.getMessage())
                            .doesNotContain(KEY).doesNotContain("secret-host");
                });
    }
}
