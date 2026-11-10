package com.pte.scoring.internal.rubric.controller;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.rubric.dto.RubricSimulationRequest;
import com.pte.scoring.internal.rubric.dto.RubricSimulationResponse;
import com.pte.scoring.internal.rubric.service.RubricSimulationService;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(RubricConstants.RUBRIC_API_PATH)
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
public class RubricSimulationController {
    private final RubricSimulationService simulations;

    public RubricSimulationController(RubricSimulationService simulations) {
        this.simulations = simulations;
    }

    @PostMapping("/simulations")
    public ApiResponse<RubricSimulationResponse> simulate(@Valid @RequestBody RubricSimulationRequest request) {
        return ApiResponse.success(simulations.simulate(request));
    }
}
