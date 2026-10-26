package com.pte.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A flat {@code internal} package with more than 15 classes is grouped into sub-packages by business concept.
 * Only {@code internal/**} is checked: the public module packages are what Spring Modulith exposes (named
 * interfaces export exactly one package), so splitting them would change the module boundary. Deliberately
 * broader than "packages carrying {@code @NamedInterface}": every public package is a boundary candidate.
 */
class PackageDensityTest {

    private static final String ALLOWLIST = "dense-packages.allowlist";
    private static final int MAX_CLASSES_PER_PACKAGE = 15;
    private static final String INTERNAL_SEGMENT = "/internal/";
    private static final String PACKAGE_INFO = "package-info.java";

    @Test
    void internalPackagesStaySmallEnoughToNavigate() {
        assertThat(SourceDebtAllowlist.ratchet(ALLOWLIST, SourceDebtAllowlist.load(ALLOWLIST),
                classesPerInternalPackage(), MAX_CLASSES_PER_PACKAGE, "group the classes into sub-packages"))
                .isEmpty();
    }

    private static Map<String, Integer> classesPerInternalPackage() {
        Map<String, Integer> counts = new TreeMap<>();
        for (Path source : SourceDebtAllowlist.javaSources()) {
            String file = SourceDebtAllowlist.key(source);
            int lastSlash = file.lastIndexOf('/');
            String directory = lastSlash < 0 ? "" : file.substring(0, lastSlash);
            if (("/" + directory + "/").contains(INTERNAL_SEGMENT) && !file.endsWith(PACKAGE_INFO)) {
                counts.merge(directory, 1, Integer::sum);
            }
        }
        return counts;
    }
}
