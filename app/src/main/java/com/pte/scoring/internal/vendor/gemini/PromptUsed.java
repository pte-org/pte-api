package com.pte.scoring.internal.vendor.gemini;

import java.util.UUID;

/**
 * Which system prompt a scoring call really sent, kept in the score detail so a score can be traced to its prompt.
 *
 * @param source          STORED (the version in use on the AI prompt pages) or CATALOG (built from the rubric
 *                        catalog because nothing is stored, e.g. before the first seed)
 * @param versionNo       the stored version number; null for CATALOG
 * @param versionPublicId the stored version; null for CATALOG
 * @param sha256          hash of the exact system text sent
 */
public record PromptUsed(String key, String source, Integer versionNo, UUID versionPublicId, String sha256) {

    public static final String SOURCE_STORED = "STORED";
    public static final String SOURCE_CATALOG = "CATALOG";
    /** Detail key under which a scorer stores it. */
    public static final String DETAIL_KEY = "prompt";
}
