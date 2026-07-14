package com.aptis.aptis_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

/**
 * Verifies the {@code scanBasePackages = "com.aptis"} fix on
 * {@link AptisApiApplication}: classes in a sibling package
 * (com.aptis.modules.iam, e.g. AdminService) must be discoverable by
 * Spring's component scan, not just classes under com.aptis.aptis_api.
 *
 * Uses ClassPathScanningCandidateComponentProvider directly (no full
 * ApplicationContext boot) to isolate this check from the unrelated,
 * pre-existing missing-DataSource gap (see plan.md Risks).
 */
class ComponentScanTest {

    @Test
    void scanBasePackagesIncludesSiblingModulesPackage() throws Exception {
        SpringBootApplication annotation = AptisApiApplication.class.getAnnotation(SpringBootApplication.class);
        String[] basePackages = annotation.scanBasePackages();

        assertThat(basePackages).contains("com.aptis");

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(true);
        Set<org.springframework.beans.factory.config.BeanDefinition> candidates =
                scanner.findCandidateComponents("com.aptis.modules.iam");

        SimpleMetadataReaderFactory readerFactory = new SimpleMetadataReaderFactory();
        boolean adminServiceFound = false;
        for (org.springframework.beans.factory.config.BeanDefinition candidate : candidates) {
            MetadataReader reader = readerFactory.getMetadataReader(candidate.getBeanClassName());
            if ("com.aptis.modules.iam.service.auth.AdminService".equals(reader.getClassMetadata().getClassName())) {
                adminServiceFound = true;
            }
        }

        assertThat(adminServiceFound)
                .as("AdminService (@Service in sibling package com.aptis.modules.iam) must be a scan candidate")
                .isTrue();
    }
}
