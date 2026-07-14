package com.aptis.modules.scoring.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptis.modules.scoring.constant.ScoringApiConstants;
import com.aptis.modules.scoring.service.ScoringService;

@RestController
@RequestMapping(ScoringApiConstants.SCORES)
public class ScoringController {

    private final ScoringService scoringService;

    public ScoringController(ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    // Skeleton — endpoints implemented later
}
