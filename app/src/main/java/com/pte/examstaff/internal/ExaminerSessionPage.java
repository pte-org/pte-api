package com.pte.examstaff.internal;

import com.pte.shared.web.PageMeta;
import java.util.List;
import java.util.Map;

public record ExaminerSessionPage(List<ExaminerSessionView> data, PageMeta meta, Map<String, Long> totalByStatus) { }
