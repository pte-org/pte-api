package com.pte.itembank.internal.repository;

/** Row of the grouped PUBLISHED+SHARED count-by-task-type query. */
public interface TaskTypeCountProjection {

    String getTaskType();

    long getCount();
}
