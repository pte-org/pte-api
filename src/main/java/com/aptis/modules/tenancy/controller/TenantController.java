package com.aptis.modules.tenancy.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.tenancy.constant.TenancyApiConstants;
import com.aptis.modules.tenancy.dto.TenantResponse;
import com.aptis.modules.tenancy.interfaces.TenantOperations;

@RestController
@RequestMapping(TenancyApiConstants.TENANTS)
public class TenantController {

    private final TenantOperations tenantService;

    public TenantController(TenantOperations tenantService) {
        this.tenantService = tenantService;
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_ADMIN)
    @GetMapping
    public List<TenantResponse> listTenants() {
        return tenantService.listTenants();
    }
}
