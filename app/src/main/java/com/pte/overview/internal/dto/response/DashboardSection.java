package com.pte.overview.internal.dto.response;

/**
 * One independently computed widget payload. {@code data} is null unless the
 * status is {@link SectionStatus#OK}; an error carries no raw message.
 */
public record DashboardSection<T>(SectionStatus status, T data) {

    public static <T> DashboardSection<T> ok(T data) {
        return new DashboardSection<>(SectionStatus.OK, data);
    }

    public static <T> DashboardSection<T> error() {
        return new DashboardSection<>(SectionStatus.ERROR, null);
    }

    public static <T> DashboardSection<T> forbidden() {
        return new DashboardSection<>(SectionStatus.FORBIDDEN, null);
    }
}
