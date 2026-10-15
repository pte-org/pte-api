package com.pte.session.internal.repository;

public interface ProctorOverviewCountsProjection {
    long getAccessible();
    long getUpcoming();
    long getToday();
}
