package com.aptis.modules.iam.dto.response.admin;

import java.util.List;
import com.aptis.modules.iam.domain.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraderResponse {
    private Long id;
    private String username;
    private UserStatus status;
    private List<Long> assignedOrgIds;
    private String rawPassword;  // One-time only on create; null on list
}
