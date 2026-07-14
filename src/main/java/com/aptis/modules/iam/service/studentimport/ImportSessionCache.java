package com.aptis.modules.iam.service.studentimport;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.domain.studentimport.ImportSession;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
public class ImportSessionCache {

    private final Cache<String, ImportSession> sessions = Caffeine.newBuilder()
            .expireAfterWrite(IamApiConstants.IMPORT_SESSION_TTL_MINUTES, TimeUnit.MINUTES)
            .maximumSize(IamApiConstants.IMPORT_MAX_SESSIONS)
            .build();

    public void saveSession(ImportSession session) {
        sessions.put(buildKey(
                session.getHostId(),
                session.getOrganizationId(),
                session.getImportId()), session);
    }

    public Optional<ImportSession> getSession(
            String hostId,
            Long organizationId,
            String importId) {
        return Optional.ofNullable(sessions.getIfPresent(
                buildKey(hostId, organizationId, importId)));
    }

    public void removeSession(
            String hostId,
            Long organizationId,
            String importId) {
        sessions.invalidate(buildKey(hostId, organizationId, importId));
    }

    private String buildKey(
            String hostId,
            Long organizationId,
            String importId) {
        return hostId + ":" + organizationId + ":" + importId;
    }
}
