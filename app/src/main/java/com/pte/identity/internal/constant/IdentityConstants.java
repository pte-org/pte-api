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
    public static final String TOO_MANY_LOGIN_ATTEMPTS = "TOO_MANY_LOGIN_ATTEMPTS";
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String IDENTITY_NOT_ELIGIBLE = "IDENTITY_NOT_ELIGIBLE";
    public static final String EMAIL_ALREADY_USED = "EMAIL_ALREADY_USED";
    public static final String HOST_CONTACT_IS_PLATFORM_ACCOUNT = "HOST_CONTACT_IS_PLATFORM_ACCOUNT";
    public static final String USER_EMAIL_REQUIRED = "USER_EMAIL_REQUIRED";
    public static final String STUDENT_ATTEMPT_IN_PROGRESS = "STUDENT_ATTEMPT_IN_PROGRESS";
    public static final String INVALID_PASSWORD_RESET_TOKEN = "INVALID_PASSWORD_RESET_TOKEN";
    public static final String PASSWORD_RESET_PATH = "/reset-password?token=";
    public static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";
    public static final String FORBIDDEN_ROLE_ASSIGNMENT = "FORBIDDEN_ROLE_ASSIGNMENT";
    public static final String FORBIDDEN_USER_MANAGEMENT = "FORBIDDEN_USER_MANAGEMENT";
    public static final String FORBIDDEN_PASSWORD_RESET = "FORBIDDEN_PASSWORD_RESET";
    public static final String PLATFORM_USER_AGGREGATE = "PlatformUser";
    public static final String PLATFORM_USER_CREATED = "PlatformUserCreated";
    public static final String PLATFORM_USER_ROLES_UPDATED = "PlatformUserRolesUpdated";
    public static final String PLATFORM_USER_PROFILE_UPDATED = "PlatformUserProfileUpdated";
    public static final String PLATFORM_USER_PASSWORD_RESET = "PlatformUserPasswordReset";
    public static final String PLATFORM_USER_SUSPENDED = "PlatformUserSuspended";
    public static final String PLATFORM_USER_REACTIVATED = "PlatformUserReactivated";
    public static final String PLATFORM_USER_ARCHIVED = "PlatformUserArchived";
    public static final String PLATFORM_USER_RESTORED = "PlatformUserRestored";
    public static final String PLATFORM_AUTHORIZATION_DENIED = "PlatformAuthorizationDenied";
    public static final String PLATFORM_AUTHORIZATION_AGGREGATE_ID = "authorization";
    public static final String PLATFORM_ROLE_ASSIGNMENT_AGGREGATE_ID = "role-assignment";
    public static final String PLATFORM_USER_CREATED_SUMMARY = "Platform user created.";
    public static final String PLATFORM_USER_ROLES_UPDATED_SUMMARY = "Platform user roles updated.";
    public static final String PLATFORM_USER_PROFILE_UPDATED_SUMMARY = "Platform user profile updated.";
    public static final String PLATFORM_USER_PASSWORD_RESET_SUMMARY = "Platform user password reset.";
    public static final String PLATFORM_USER_SUSPENDED_SUMMARY = "Platform user suspended.";
    public static final String PLATFORM_USER_REACTIVATED_SUMMARY = "Platform user reactivated.";
    public static final String PLATFORM_USER_ARCHIVED_SUMMARY = "Platform user archived.";
    public static final String PLATFORM_USER_RESTORED_SUMMARY = "Platform user restored.";
    public static final String PLATFORM_AUTHORIZATION_DENIED_SUMMARY =
            "Platform-user management authorization denied.";
    public static final String USER_AGGREGATE = "User";
    public static final String USER_PASSWORD_RESET = "UserPasswordReset";
    public static final String USER_PASSWORD_RESET_SUMMARY = "Authorized administrator reset the password.";
    public static final String USER_CREDENTIALS_EMAILED = "UserCredentialsEmailed";
    public static final String USER_CREDENTIALS_EMAILED_SUMMARY = "Authorized administrator e-mailed a temporary password.";
    public static final String USER_PASSWORD_CHANGED_SELF = "UserPasswordChangedSelf";
    public static final String USER_PASSWORD_CHANGED_SELF_SUMMARY = "User changed their own password.";
    public static final String USER_PASSWORD_RESET_SELF = "UserPasswordResetSelf";
    public static final String USER_PASSWORD_RESET_SELF_SUMMARY = "User reset their own password from an e-mailed link.";
    public static final String USER_PROFILE_UPDATED = "UserProfileUpdated";
    public static final String USER_PROFILE_UPDATED_SUMMARY = "User updated their profile.";
    public static final String INVALID_EXAM_STAFF_QUERY = "INVALID_EXAM_STAFF_QUERY";
    public static final String DUPLICATE_EMAIL_IN_BATCH = "DUPLICATE_EMAIL_IN_BATCH";
    public static final String ROSTER_IMPORT_FAILED = "ROSTER_IMPORT_FAILED";
    public static final int ROSTER_USERNAME_COLLISION_RETRIES = 3;

    /** Whole roster rejected because at least one row breaks a format rule; {@code data} lists every row error. */
    public static final String ROSTER_VALIDATION_FAILED = "ROSTER_VALIDATION_FAILED";
    public static final String ROSTER_FIELD_EMAIL = "email";
    public static final String ROSTER_FIELD_FULL_NAME = "fullName";
    public static final String ROSTER_FIELD_STUDENT_CODE = "studentCode";
    public static final String ROSTER_FIELD_CLASS_NAME = "className";
    public static final String ROSTER_FIELD_PHONE = "phone";
    public static final String ROSTER_FIELD_DATE_OF_BIRTH = "dateOfBirth";
    public static final String ROSTER_EMAIL_REQUIRED = "EMAIL_REQUIRED";
    public static final String ROSTER_EMAIL_INVALID = "EMAIL_INVALID";
    public static final String ROSTER_EMAIL_TOO_LONG = "EMAIL_TOO_LONG";
    public static final String ROSTER_FULL_NAME_REQUIRED = "FULL_NAME_REQUIRED";
    public static final String ROSTER_FULL_NAME_TOO_LONG = "FULL_NAME_TOO_LONG";
    public static final String ROSTER_STUDENT_CODE_TOO_LONG = "STUDENT_CODE_TOO_LONG";
    public static final String ROSTER_CLASS_NAME_TOO_LONG = "CLASS_NAME_TOO_LONG";
    public static final String ROSTER_PHONE_INVALID = "PHONE_INVALID";
    public static final String ROSTER_DATE_OF_BIRTH_INVALID = "DATE_OF_BIRTH_INVALID";
    public static final int ROSTER_MAX_EMAIL_LENGTH = 254;
    public static final int ROSTER_MAX_FULL_NAME_LENGTH = 100;
    public static final int ROSTER_MAX_STUDENT_CODE_LENGTH = 50;
    public static final int ROSTER_MAX_CLASS_NAME_LENGTH = 100;
    /** Optional leading +, then 9–15 digits, after spaces, dots and dashes are removed. */
    public static final String ROSTER_PHONE_PATTERN = "^\\+?\\d{9,15}$";
    public static final String ROSTER_PHONE_SEPARATORS = "[\\s.\\-]";
    /** Same shape as Jakarta {@code @Email}'s practical subset: one @, a dot in the domain, no spaces. */
    public static final String ROSTER_EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    public static final String USERNAME_REQUIRED = "Username is required";
    public static final String EMAIL_REQUIRED = "Email is required";
    public static final String EMAIL_INVALID = "Email must be valid";
    public static final String FULL_NAME_REQUIRED = "Full name is required";
    public static final String FULL_NAME_TOO_LONG = "Full name must be at most 255 characters";
    public static final String PHONE_TOO_LONG = "Phone number must be at most 255 characters";
    public static final String DATE_OF_BIRTH_NOT_IN_FUTURE = "Date of birth cannot be in the future";
    public static final String PASSWORD_REQUIRED = "Password is required";
    public static final String PASSWORD_MIN_LENGTH = "Password must be at least 8 characters";
    public static final String AT_LEAST_ONE_ROLE_REQUIRED = "At least one role is required";
    public static final String AT_LEAST_ONE_ROW_REQUIRED = "At least one row is required";
    public static final String REFRESH_TOKEN_REQUIRED = "Refresh token is required";
    public static final String CURRENT_PASSWORD_REQUIRED = "Current password is required";
    public static final String NEW_PASSWORD_REQUIRED = "New password is required";
    public static final String RESET_TOKEN_REQUIRED = "Reset token is required";
    public static final String PRACTICE_IDENTITY_NOT_FOUND = "PRACTICE_IDENTITY_NOT_FOUND";
    public static final String PRACTICE_IDENTITY_EMAIL_MISMATCH = "PRACTICE_IDENTITY_EMAIL_MISMATCH";

    public static final String JWT_DECODER_BUILD_FAILED = "Failed to build JWT decoder";
    public static final String RSA_KEY_GENERATION_FAILED = "RSA key generation failed";
    public static final String INVALID_RSA_PRIVATE_KEY =
            "identity.rsa-private-key-pem is set but is not a valid PKCS#8 PEM-encoded RSA private key";
    public static final String SHA256_UNAVAILABLE = "SHA-256 unavailable";

    public static final String KEY_ID = "iam-rsa-key";
    public static final String TOKEN_ISSUER = "pte-iam";
    public static final long DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 900L;
    public static final long DEFAULT_REFRESH_TOKEN_TTL_SECONDS = 604_800L;
    public static final String ACCESS_TOKEN_TTL_INVALID = "Access token TTL must be a positive number of seconds";
    public static final String REFRESH_TOKEN_TTL_INVALID = "Refresh token TTL must be a positive number of seconds";
    public static final String REFRESH_TOKEN_TTL_ORDER_INVALID = "Refresh token TTL must exceed access token TTL";

    private IdentityConstants() {
    }
    public static final String USER_STATUS_ACTION_UNSUPPORTED = "USER_STATUS_ACTION_UNSUPPORTED";
}
