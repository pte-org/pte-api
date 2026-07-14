package com.aptis.modules.iam.interfaces;

import com.aptis.modules.iam.dto.request.admin.CreateHostRequest;
import com.aptis.modules.iam.dto.response.admin.HostResponse;

public interface HostManagement {
    HostResponse createHost(CreateHostRequest request);
}
