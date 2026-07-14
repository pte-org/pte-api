package com.aptis.modules.scoring.dto.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerResponse {
    private Long id;
    private Long questionId;
    private String questionType;
    private String content;
    private Long selectedOptionId;
    private Boolean isCorrect;
    private BigDecimal manualScore;
}
