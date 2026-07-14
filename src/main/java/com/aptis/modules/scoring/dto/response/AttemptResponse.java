package com.aptis.modules.scoring.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptResponse {
    private Long id;
    private String studentId;
    private Long examId;
    private Boolean isSubmitted;
    private LocalDateTime submittedAt;
    private int answerCount;
}
