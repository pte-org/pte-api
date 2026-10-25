package com.pte.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Production sources plus a ratchet allowlist of known debt ({@code key=limit} per line, {@code #} comments).
 * Each limit is the exact current measurement, so the list can only shrink: exceeding a limit, new debt outside
 * the list and a limit higher than the code needs all fail, otherwise a fixed file could quietly regrow.
 *
 * <p>Sources are read relative to the {@code app} module directory (Maven's working directory); a run from the
 * {@code pte-api} root is also accepted.
 */
final class SourceDebtAllowlist {

    private static final Path MODULE_SOURCES = Path.of("src", "main", "java");
    private static final Path REPOSITORY_SOURCES = Path.of("app").resolve(MODULE_SOURCES);
    private static final Path ALLOWLIST_DIR = Path.of("src", "test", "resources", "architecture");
    private static final char ENTRY_SEPARATOR = '=';
    private static final String COMMENT_PREFIX = "#";

    private SourceDebtAllowlist() {
    }

    static List<Path> javaSources() {
        try (Stream<Path> files = Files.walk(mainSources())) {
            return files.filter(file -> file.toString().endsWith(".java")).sorted().toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Path relative to the source root with forward slashes, the key format of the allowlists. */
    static String key(Path source) {
        return mainSources().relativize(source).toString().replace('\\', '/');
    }

    static List<String> lines(Path source) {
        try {
            return Files.readAllLines(source, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static Map<String, Integer> load(String allowlistName) {
        Path allowlist = moduleRoot().resolve(ALLOWLIST_DIR).resolve(allowlistName);
        Map<String, Integer> limits = new TreeMap<>();
        List<String> entries = lines(allowlist);
        for (int index = 0; index < entries.size(); index++) {
            String entry = entries.get(index).strip();
            if (entry.isEmpty() || entry.startsWith(COMMENT_PREFIX)) {
                continue;
            }
            int separator = entry.lastIndexOf(ENTRY_SEPARATOR);
            Integer limit = separator > 0 ? parseLimit(entry.substring(separator + 1)) : null;
            if (limit == null) {
                throw malformed(allowlistName, index, entry, "expected <key>=<positive number>");
            }
            if (limits.put(entry.substring(0, separator), limit) != null) {
                throw malformed(allowlistName, index, entry, "duplicate key");
            }
        }
        return limits;
    }

    /**
     * Compares measurements above {@code floor} (values at or below it are not debt) with the allowlist.
     *
     * @param fixHint what the author should do when a key exceeds its limit or is new debt
     */
    static List<String> ratchet(String allowlistName, Map<String, Integer> allowed, Map<String, Integer> measured,
            int floor, String fixHint) {
        List<String> violations = new ArrayList<>();
        measured.forEach((key, value) -> {
            if (value <= floor) {
                return;
            }
            Integer limit = allowed.get(key);
            if (limit == null || value > limit) {
                violations.add(key + " measures " + value + " (allowed " + (limit == null ? floor : limit) + "); "
                        + fixHint);
            } else if (value < limit) {
                violations.add(allowlistName + ": lower " + key + " to " + value);
            }
        });
        allowed.keySet().stream()
                .filter(key -> measured.getOrDefault(key, 0) <= floor)
                .forEach(key -> violations.add(allowlistName + ": remove " + key + ", it is no longer debt"));
        return violations;
    }

    private static Integer parseLimit(String value) {
        try {
            int limit = Integer.parseInt(value.strip());
            return limit > 0 ? limit : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static IllegalStateException malformed(String allowlistName, int index, String entry, String problem) {
        return new IllegalStateException(allowlistName + " line " + (index + 1) + " '" + entry + "': " + problem);
    }

    private static Path mainSources() {
        return moduleRoot().resolve(MODULE_SOURCES);
    }

    private static Path moduleRoot() {
        if (Files.isDirectory(MODULE_SOURCES)) {
            return Path.of("");
        }
        if (Files.isDirectory(REPOSITORY_SOURCES)) {
            return Path.of("app");
        }
        throw new IllegalStateException("Run from pte-api/app or pte-api: " + MODULE_SOURCES + " not found under "
                + Path.of("").toAbsolutePath());
    }
}
