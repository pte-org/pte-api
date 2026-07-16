package com.aptis.modules.iam.dto.response.host;

import com.aptis.modules.iam.domain.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProctorResponse {
    private Long id;
    private String username;
    private String fullName;
    private UserStatus status;
    private String rawPassword; // One-time only on create; null on list
}
