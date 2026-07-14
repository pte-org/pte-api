package com.aptis.modules.iam.interfaces;

public interface StudentImportConfirmer {
    byte[] confirm(String hostId, Long organizationId, String importId);
}
