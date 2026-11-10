package com.pte.assessment.internal.service.standard;

import java.util.List;

/** Whether a host template still follows the platform template it was cloned from, and the reasons if not. */
public record StandardCheckResult(boolean standard, List<String> deviations) {
}
