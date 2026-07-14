package com.aptis.modules.iam.constant;

public final class IamMessageConstants {

    public static final String JWT_SECRET_MISSING = "JWT secret is required";
    public static final String JWT_SECRET_TOO_SHORT = "JWT secret must be at least 32 bytes";
    public static final String ADMIN_SEED_EMAIL_MISSING = "ADMIN_SEED_EMAIL is required when admin table is empty";
    public static final String ADMIN_SEED_PASSWORD_MISSING = "ADMIN_SEED_PASSWORD is required when admin table is empty";
    public static final String CREDENTIAL_TOO_SHORT = "Credential is too short";
    public static final String HOST_ALREADY_EXISTS = "Host already exists";
    public static final String HOST_SEED_INCOMPLETE = "Host seed environment variables are incomplete";
    public static final String STUDENT_SEED_INCOMPLETE = "Student seed environment variables are incomplete";
    public static final String IMPORT_FILE_REQUIRED = "Import file is required";
    public static final String IMPORT_FILE_TYPE_INVALID = "Only .xls and .xlsx files are supported";
    public static final String IMPORT_FILE_EMPTY = "Import file has no data rows";
    public static final String IMPORT_TOO_MANY_ROWS = "Import file exceeds the row limit";
    public static final String IMPORT_SESSION_NOT_FOUND = "Import session was not found or has expired";
    public static final String IMPORT_ALREADY_CONFIRMED = "Import session was already confirmed";
    public static final String IMPORT_PREVIEW_REQUIRED = "Preview must be completed before confirm";
    public static final String IMPORT_VALIDATION_FAILED = "Import validation failed";
    public static final String MISSING_FULL_NAME = "MISSING_FULL_NAME";
    public static final String INVALID_EMAIL_FORMAT = "INVALID_EMAIL_FORMAT";
    public static final String INVALID_DATE_FORMAT = "INVALID_DATE_FORMAT";
    public static final String DUPLICATE_EMAIL = "DUPLICATE_EMAIL";
    public static final String USERNAME_COLLISION_RESOLVED = "USERNAME_COLLISION_RESOLVED";
    public static final String USERNAME_COLLISION_UNRESOLVED = "USERNAME_COLLISION_UNRESOLVED";
    public static final String USERNAME_STRATEGY_FALLBACK = "USERNAME_STRATEGY_FALLBACK";

    private IamMessageConstants() {
    }
}
