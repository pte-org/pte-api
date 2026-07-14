package com.aptis.modules.iam.dto.request.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignOrgRequest {

    @NotNull
    private Long organizationId;
}
