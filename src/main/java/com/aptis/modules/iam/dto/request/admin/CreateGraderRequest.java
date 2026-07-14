package com.aptis.modules.iam.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateGraderRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    private String username;
}
