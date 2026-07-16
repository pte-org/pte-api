package com.aptis.modules.iam.interfaces;

import java.util.List;

import com.aptis.modules.iam.dto.request.host.CreateProctorRequest;
import com.aptis.modules.iam.dto.response.host.ProctorResponse;

public interface HostProctorOperations {
    ProctorResponse createProctor(Long organizationId, String hostId, CreateProctorRequest request);

    List<ProctorResponse> listProctors(Long organizationId);
}
