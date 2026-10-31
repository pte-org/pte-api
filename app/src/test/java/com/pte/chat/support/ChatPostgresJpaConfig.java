package com.pte.chat.support;

import com.pte.chat.internal.domain.ChatMessage;
import com.pte.chat.internal.repository.ChatMessageRepository;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Context pieces shared by the chat tests that need the JPA repositories on the shared Postgres. */
public final class ChatPostgresJpaConfig {

    private ChatPostgresJpaConfig() {
    }

    /** Only the chat entities and repositories; no other module is loaded. */
    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = ChatMessage.class)
    @EnableJpaRepositories(basePackageClasses = ChatMessageRepository.class)
    public static class Jpa {
    }

    /** Points the datasource at the shared container; runs only when a context is really built. */
    public static class PostgresInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            PostgreSQLContainer postgres = ChatSharedPostgres.container();
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "spring.datasource.url=" + postgres.getJdbcUrl(),
                    "spring.datasource.username=" + postgres.getUsername(),
                    "spring.datasource.password=" + postgres.getPassword(),
                    "spring.datasource.driver-class-name=org.postgresql.Driver");
        }
    }
}
