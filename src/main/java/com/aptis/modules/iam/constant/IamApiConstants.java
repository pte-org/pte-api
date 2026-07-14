package com.aptis.modules.iam.constant;

import com.aptis.common.constant.ApiVersion;

public final class IamApiConstants {

    // Full auth endpoint paths
    public static final String AUTH_BASE = ApiVersion.V1 + "/auth";
    public static final String AUTH_PATTERN = AUTH_BASE + "/**";
    public static final String AUTH_PREFIX = AUTH_BASE + "/";
    public static final String AUTH_LOGIN = AUTH_BASE + "/login";
    public static final String AUTH_REFRESH = AUTH_BASE + "/refresh";
    public static final String AUTH_LOGOUT = AUTH_BASE + "/logout";
    public static final String AUTH_ME = AUTH_BASE + "/me";
    public static final String AUTH_CHANGE_PASSWORD = AUTH_BASE + "/change-password";

    // Full admin endpoint paths
    public static final String ADMIN_BASE = ApiVersion.V1 + "/admin";
    public static final String ADMIN_HOSTS = ADMIN_BASE + "/hosts";
    public static final String ADMIN_GRADERS = ADMIN_BASE + "/graders";

    // Full host endpoint paths
    public static final String HOST_STUDENTS_BASE = ApiVersion.V1 + "/host/students";
    public static final String HOST_STUDENT_IMPORT_BASE = ApiVersion.V1 + "/host/students/import";

    // Sub-paths (used in @PostMapping / @GetMapping within controllers)
    public static final String PATH_LOGIN = "/login";
    public static final String PATH_REFRESH = "/refresh";
    public static final String PATH_LOGOUT = "/logout";
    public static final String PATH_ME = "/me";
    public static final String PATH_CHANGE_PASSWORD = "/change-password";
    public static final String PATH_HOSTS = "/hosts";
    public static final String PATH_GRADERS = "/graders";
    public static final String PATH_GRADER_ORGS = "/{graderId}/orgs";
    public static final String PATH_GRADER_ORG_REVOKE = "/{graderId}/orgs/{orgId}";
    public static final String PATH_IMPORT_PARSE = "/parse";
    public static final String PATH_IMPORT_PREPARE = "/prepare";
    public static final String PATH_IMPORT_PREVIEW = "/preview";
    public static final String PATH_IMPORT_CONFIRM = "/confirm";

    // Student import
    public static final int IMPORT_SAMPLE_ROW_COUNT = 5;
    public static final int IMPORT_MAX_ROWS = 10000;
    public static final long IMPORT_MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;
    public static final long IMPORT_MAX_SESSIONS = 1000;
    public static final int IMPORT_SESSION_TTL_MINUTES = 30;
    public static final int USERNAME_COLLISION_SUFFIX_LIMIT = 9999;
    public static final String EXCEL_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String EXCEL_EXTENSION_XLS = ".xls";
    public static final String EXCEL_EXTENSION_XLSX = ".xlsx";
    public static final String IMPORT_USERNAME_COLUMN = "username";
    public static final String IMPORT_PASSWORD_COLUMN = "password";
    public static final String IMPORT_EXCEL_SHEET_NAME = "Students";
    public static final String USERNAME_AUTO_PREFIX = "student";
    public static final String USERNAME_ORG_PREFIX = "org";

    // OpenAPI / Swagger paths
    public static final String API_DOCS_PATTERN = "/v3/api-docs/**";
    public static final String SWAGGER_UI_PATTERN = "/swagger-ui/**";
    public static final String SWAGGER_UI_HTML = "/swagger-ui.html";

    // Roles
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_HOST = "HOST";
    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_GRADER = "GRADER";

    // User types
    public static final String USER_TYPE_ADMIN = "ADMIN";
    public static final String USER_TYPE_HOST = "HOST";
    public static final String USER_TYPE_STUDENT = "STUDENT";
    public static final String USER_TYPE_GRADER = "GRADER";

    // Status values
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";
    public static final String STATUS_LOCKED = "LOCKED";

    // JWT claim keys
    public static final String JWT_CLAIM_ROLE = "role";
    public static final String JWT_CLAIM_USER_TYPE = "userType";
    public static final String JWT_CLAIM_TENANT_ID = "tenantId";

    // HTTP auth headers
    public static final String TOKEN_TYPE_BEARER = "Bearer";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    // Media type
    public static final String JSON_CONTENT_TYPE = "application/json";

    // Cryptographic algorithm
    public static final String SHA_256_ALGORITHM = "SHA-256";

    // Spring Security expressions for @PreAuthorize
    public static final String AUTHORITY_ADMIN = "hasAuthority('ADMIN')";
    public static final String AUTHORITY_HOST = "hasAuthority('HOST')";
    public static final String AUTHORITY_STUDENT = "hasAuthority('STUDENT')";
    public static final String AUTHORITY_GRADER = "hasAuthority('GRADER')";

    // Security configuration
    public static final int BCRYPT_COST = 12;
    public static final int IMPORT_BCRYPT_COST = 10;
    public static final int IMPORT_CREDENTIAL_HASH_THREADS = 4;
    public static final int JWT_MIN_SECRET_BYTES = 32;
    public static final int REFRESH_TOKEN_DAYS = 30;
    public static final int GENERATED_PASSWORD_LENGTH = 12;
    public static final int MIN_CREDENTIAL_LENGTH = 8;
    public static final String GENERATED_PASSWORD_CHARSET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$";

    // Grader error messages
    public static final String GRADER_NOT_FOUND = "GRADER_NOT_FOUND";
    public static final String GRADER_USERNAME_ALREADY_EXISTS = "GRADER_USERNAME_ALREADY_EXISTS";
    public static final String GRADER_ORG_ASSIGNMENT_ALREADY_EXISTS = "GRADER_ORG_ASSIGNMENT_ALREADY_EXISTS";
    public static final String GRADER_ORG_NOT_ASSIGNED = "GRADER_ORG_NOT_ASSIGNED";
    public static final String GRADER_NOT_ACTIVE = "GRADER_NOT_ACTIVE";

    // Operation codes (for audit / logging)
    public static final String OP_ADMIN_CREATE = "iam.admin.create";
    public static final String OP_ADMIN_CREATE_GRADER = "iam.admin.create-grader";
    public static final String OP_AUTH_LOGIN = "iam.auth.login";
    public static final String OP_AUTH_REFRESH = "iam.auth.refresh";
    public static final String OP_AUTH_LOGOUT = "iam.auth.logout";
    public static final String OP_AUTH_CHANGE_PASSWORD = "iam.auth.change-password";
    public static final String OP_HOST_CREATE = "iam.host.create";

    // Seed data
    public static final String SYSTEM_ADMIN_NAME = "System Administrator";

    // Admin seed env var names
    public static final String ADMIN_SEED_EMAIL_ENV = "ADMIN_SEED_EMAIL";
    public static final String ADMIN_SEED_PASSWORD_ENV = "ADMIN_SEED_PASSWORD";

    // Host seed env var names
    public static final String HOST_SEED_CODE_ENV = "HOST_SEED_CODE";
    public static final String HOST_SEED_NAME_ENV = "HOST_SEED_NAME";
    public static final String HOST_SEED_EMAIL_ENV = "HOST_SEED_EMAIL";
    public static final String HOST_SEED_PASSWORD_ENV = "HOST_SEED_PASSWORD";
    public static final String HOST_SEED_ORGANIZATION_NAME_ENV = "HOST_SEED_ORGANIZATION_NAME";
    public static final String HOST_SEED_ORGANIZATION_TYPE_ENV = "HOST_SEED_ORGANIZATION_TYPE";
    public static final String HOST_SEED_ORGANIZATION_ADDRESS_ENV = "HOST_SEED_ORGANIZATION_ADDRESS";
    public static final String HOST_SEED_REPRESENTATIVE_NAME_ENV = "HOST_SEED_REPRESENTATIVE_NAME";
    public static final String HOST_SEED_REPRESENTATIVE_PHONE_ENV = "HOST_SEED_REPRESENTATIVE_PHONE";
    public static final String HOST_SEED_CONTRACT_CODE_ENV = "HOST_SEED_CONTRACT_CODE";
    public static final String HOST_SEED_PACKAGE_NAME_ENV = "HOST_SEED_PACKAGE_NAME";
    public static final String HOST_SEED_STUDENT_LIMIT_ENV = "HOST_SEED_STUDENT_LIMIT";

    // Student seed env var names
    public static final String STUDENT_SEED_USERNAME_ENV = "STUDENT_SEED_USERNAME";
    public static final String STUDENT_SEED_PASSWORD_ENV = "STUDENT_SEED_PASSWORD";
    public static final String STUDENT_SEED_FULL_NAME_ENV = "STUDENT_SEED_FULL_NAME";
    public static final String STUDENT_SEED_CODE_ENV = "STUDENT_SEED_CODE";
    public static final String STUDENT_SEED_CLASS_NAME_ENV = "STUDENT_SEED_CLASS_NAME";
    public static final String STUDENT_SEED_EMAIL_ENV = "STUDENT_SEED_EMAIL";
    public static final String STUDENT_SEED_PHONE_ENV = "STUDENT_SEED_PHONE";

    private IamApiConstants() {
    }
}
