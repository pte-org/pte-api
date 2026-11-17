package com.pte.identity.internal.controller;

import com.pte.identity.internal.dto.request.auth.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One username is one account in one organization, so login sends a username and a secret and nothing else; the
 * organization picker and its {@code /auth/login-options} endpoint no longer exist.
 */
class AuthControllerLoginContractTest {

    @Test
    void loginOptionsEndpointIsGone() {
        List<String> postPaths = Arrays.stream(AuthController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(mapping -> mapping != null)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .toList();

        assertThat(postPaths).contains("/login").doesNotContain("/login-options");
    }

    @Test
    void loginRequestCarriesOnlyUsernameAndSecret() {
        List<String> components = Arrays.stream(LoginRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(components).containsExactly("username", "password");
    }
}
