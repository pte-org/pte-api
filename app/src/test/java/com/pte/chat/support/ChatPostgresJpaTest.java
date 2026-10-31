package com.pte.chat.support;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JPA slice over the shared Postgres with the real Flyway schema ({@code postgres-test} profile). The test
 * transaction is switched off on purpose: the concurrency tests need every statement to commit, and the
 * services under test own their (short) transactions. Skipped when Docker is unavailable.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres-test")
@ContextConfiguration(classes = ChatPostgresJpaConfig.Jpa.class,
        initializers = ChatPostgresJpaConfig.PostgresInitializer.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
public @interface ChatPostgresJpaTest {
}
