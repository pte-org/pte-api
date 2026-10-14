package com.pte.overview.internal.constant;

import java.time.Duration;
import java.time.ZoneId;
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
    public static final String CACHE_SCOPE_HOST = "host";
    public static final Duration HOST_SERIES_TTL = Duration.ofSeconds(30);
    public static final String SERIES_ATTEMPTS_MONTHLY = "attempts-monthly";

    /** The grading backlog is the only cached commercial/ops section. */
    public static final String CACHE_SECTION_GRADING = "grading";
    public static final Duration HOST_GRADING_TTL = Duration.ofSeconds(30);
    public static final int RECENT_ACTIVITY_LIMIT = 8;

    public static final String SECTION_LEARNERS = "learners";
    public static final String SECTION_QUOTA = "quota";
    public static final String SECTION_ACADEMICS = "academics";
    public static final String SECTION_STAFF = "staff";
    public static final String SECTION_EXAMS = "exams";
    public static final String SECTION_ATTEMPTS = "attempts";
    public static final String SECTION_GRADING = "grading";
    public static final String SECTION_SUBSCRIPTION = "subscription";
    public static final String SECTION_BILLING = "billing";
    public static final String SECTION_SUPPORT = "support";
    public static final String SECTION_RECENT_ACTIVITY = "recentActivity";

    public static final String ADMIN_PATH = "/admin";
    public static final ZoneId BUCKET_ZONE_ID = ZoneId.of(BUCKET_ZONE);

    /**
     * Admin sections are cached one by one for 60 seconds under a key that embeds the caller's
     * computed visibility bucket (never the raw role), the window and the schema version.
     */
    public static final String CACHE_SCOPE_ADMIN = "admin";
    public static final Duration ADMIN_SECTION_TTL = Duration.ofSeconds(60);
    public static final String CACHE_BUCKET_NONE = "none";
    public static final String CACHE_BUCKET_SEPARATOR = "+";

    public static final int RECENT_TENANTS_LIMIT = 5;
    public static final int TOP_UTILISATION_LIMIT = 5;
    public static final int OLDEST_APPLICATIONS_LIMIT = 5;
    public static final int OLDEST_TICKETS_LIMIT = 5;

    public static final String SECTION_TENANTS = "tenants";
    public static final String SECTION_RECENT_TENANTS = "recentTenants";
    public static final String SECTION_APPLICATIONS = "applications";
    public static final String SECTION_LICENSES = "licenses";
    public static final String SECTION_SUBSCRIPTIONS = "subscriptions";
    public static final String SECTION_PLANS = "plans";
    public static final String SECTION_REVENUE = "revenue";
    public static final String SECTION_QUESTION_BANK = "questionBank";

    /** Revenue covers online (PayOS) paid orders only; license-code activations carry no payment. */
    public static final String REVENUE_SOURCE_ONLINE_ORDERS = "ONLINE_ORDERS";

    private OverviewConstants() {
    }
}
