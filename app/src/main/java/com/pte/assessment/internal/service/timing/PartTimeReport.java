package com.pte.assessment.internal.service.timing;

import com.pte.assessment.dto.response.ExamTimeFrameResponse;
import com.pte.assessment.dto.response.PartTimeFeasibilityResponse;

import java.util.List;

/** Puts each part's planned duration next to the template's window for it. */
public final class PartTimeReport {

    private PartTimeReport() {
    }

    public static List<PartTimeFeasibilityResponse> of(List<PartTimeRange> ranges, ExamTimeFrameResponse frame) {
        return ranges.stream().map(range -> toResponse(range, frame)).toList();
    }

    private static PartTimeFeasibilityResponse toResponse(PartTimeRange range, ExamTimeFrameResponse frame) {
        if (frame == null) {
            return new PartTimeFeasibilityResponse(range.part().name(), range.minSeconds(), range.maxSeconds(),
                    null, null, null);
        }
        int frameMin = frameMin(range.part(), frame);
        int frameMax = frameMax(range.part(), frame);
        // Some count within the template's range lands in the window when the two ranges overlap.
        boolean canFit = range.minSeconds() <= frameMax && range.maxSeconds() >= frameMin;
        return new PartTimeFeasibilityResponse(range.part().name(), range.minSeconds(), range.maxSeconds(),
                frameMin, frameMax, canFit);
    }

    private static int frameMin(ExamTimePart part, ExamTimeFrameResponse frame) {
        return switch (part) {
            case SPEAKING_WRITING -> frame.speakingWritingMinSeconds();
            case READING -> frame.readingMinSeconds();
            case LISTENING -> frame.listeningMinSeconds();
        };
    }

    private static int frameMax(ExamTimePart part, ExamTimeFrameResponse frame) {
        return switch (part) {
            case SPEAKING_WRITING -> frame.speakingWritingMaxSeconds();
            case READING -> frame.readingMaxSeconds();
            case LISTENING -> frame.listeningMaxSeconds();
        };
    }
}
