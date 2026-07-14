package com.aptis.modules.iam.interfaces;

import com.aptis.modules.iam.dto.response.admin.AdminResponse;
import com.aptis.modules.iam.dto.request.admin.CreateAdminRequest;

public interface AdminOperations {

    AdminResponse createAdmin(CreateAdminRequest request);
}
