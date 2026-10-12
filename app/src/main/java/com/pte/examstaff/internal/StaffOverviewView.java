package com.pte.examstaff.internal;

import java.util.Map;

public record StaffOverviewView(Map<String, Long> proctor, ExaminerOverviewView examiner) { }
