package com.pte.overview.internal.constant;

import java.time.Duration;
import java.util.Set;

/** Error codes, limits and cache settings owned by the overview module. */
public final class OverviewConstants {

    public static final String API_BASE = "/api/v1/dashboard";
    public static final String HOST_PATH = "/host";
    public static final String PARAM_WINDOW_DAYS = "windowDays";

    public static final String INVALID_DASHBOARD_WINDOW = "INVALID_DASHBOARD_WINDOW";

    public static final int DEFAULT_WINDOW_DAYS = 30;
    public static final Set<Integer> ALLOWED_WINDOW_DAYS = Set.of(7, 30, 90);

    /** Month buckets and window echo use the product's home time zone, never UTC. */
    public static final String BUCKET_ZONE = "Asia/Ho_Chi_Minh";
    public static final int SERIES_MONTHS = 12;
    public static final int UPCOMING_SESSIONS_LIMIT = 5;

    /** Bump when a cached payload shape changes so stale entries are never deserialised. */
    public static final String CACHE_SCHEMA_VERSION = "v1";
    public static final String CACHE_KEY_PREFIX = "dash";
    public static final Duration HOST_SERIES_TTL = Duration.ofSeconds(30);
    public static final String SERIES_ATTEMPTS_MONTHLY = "attempts-monthly";

    public static final String SECTION_LEARNERS = "learners";
    public static final String SECTION_QUOTA = "quota";
    public static final String SECTION_ACADEMICS = "academics";
    public static final String SECTION_STAFF = "staff";
    public static final String SECTION_EXAMS = "exams";
    public static final String SECTION_ATTEMPTS = "attempts";

    private OverviewConstants() {
    }
}
