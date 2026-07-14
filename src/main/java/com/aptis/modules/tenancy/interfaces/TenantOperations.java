package com.aptis.modules.tenancy.interfaces;

import java.util.List;

import com.aptis.modules.tenancy.dto.TenantResponse;

public interface TenantOperations {

    List<TenantResponse> listTenants();

    TenantResponse getTenant(Long id);
}
