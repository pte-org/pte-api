package com.pte.identity.internal.constant;

/**
 * Centralized codes/labels for identity (no hardcoded strings in logic, per
 * standard). Error codes are machine-readable and returned in
 * {@code ApiResponse.message}.
 *
 * <p>Trimmed from the microservice-era {@code IamConstants}: no synchronization message
 * aggregate/event-type constants (no synchronization message in the monolith) and no incoming
 * tenant-event constants (see {@code UserService.me()} — tenant lookup moves
 * to an in-process call once Phase 03 ports {@code tenancy}, not an event
 * read model).
 */
public final class IdentityConstants {

    public static final String INVALID_LOGIN = "INVALID_LOGIN";
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String EMAIL_ALREADY_USED = "EMAIL_ALREADY_USED";
    public static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";
    public static final String FORBIDDEN_ROLE_ASSIGNMENT = "FORBIDDEN_ROLE_ASSIGNMENT";
    public static final String FORBIDDEN_PASSWORD_RESET = "FORBIDDEN_PASSWORD_RESET";
    public static final String DUPLICATE_EMAIL_IN_BATCH = "DUPLICATE_EMAIL_IN_BATCH";

    public static final String USERNAME_REQUIRED = "Username is required";
    public static final String EMAIL_REQUIRED = "Email is required";
    public static final String EMAIL_INVALID = "Email must be valid";
    public static final String FULL_NAME_REQUIRED = "Full name is required";
    public static final String PASSWORD_REQUIRED = "Password is required";
    public static final String PASSWORD_MIN_LENGTH = "Password must be at least 8 characters";
    public static final String AT_LEAST_ONE_ROLE_REQUIRED = "At least one role is required";
    public static final String AT_LEAST_ONE_ROW_REQUIRED = "At least one row is required";
    public static final String REFRESH_TOKEN_REQUIRED = "Refresh token is required";

    public static final String JWT_DECODER_BUILD_FAILED = "Failed to build JWT decoder";
    public static final String RSA_KEY_GENERATION_FAILED = "RSA key generation failed";
    public static final String INVALID_RSA_PRIVATE_KEY =
            "identity.rsa-private-key-pem is set but is not a valid PKCS#8 PEM-encoded RSA private key";
    public static final String SHA256_UNAVAILABLE = "SHA-256 unavailable";

    public static final String KEY_ID = "iam-rsa-key";
    public static final String TOKEN_ISSUER = "pte-iam";
    public static final long ACCESS_TOKEN_TTL_SECONDS = 900L;      // 15 minutes
    public static final long REFRESH_TOKEN_TTL_SECONDS = 604_800L; // 7 days

    private IdentityConstants() {
    }
}
