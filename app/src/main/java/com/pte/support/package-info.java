@org.springframework.modulith.ApplicationModule(
        displayName = "Support",
        allowedDependencies = {"shared", "identity", "tenancy", "tenancy::domain",
                "itembank", "session", "attempt"})
package com.pte.support;
