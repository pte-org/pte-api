package com.pte.examstaff.internal;

import com.pte.scoring.dto.response.ExaminerProgressView;
import com.pte.session.dto.response.StaffSessionView;

public record ExaminerSessionView(StaffSessionView session, ExaminerProgressView progress) { }
