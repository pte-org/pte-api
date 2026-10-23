package com.pte.rubric.internal.service;

import com.pte.rubric.RubricPublishedProjection;
import com.pte.rubric.domain.RubricRevision;
import com.pte.rubric.domain.enums.RubricChannel;
import com.pte.rubric.domain.enums.RubricStatus;
import com.pte.rubric.dto.response.RubricResponse;
import com.pte.rubric.dto.response.RubricDiffResponse;
import com.pte.rubric.internal.exception.RubricNotFoundException;
import com.pte.rubric.internal.mapper.RubricMapper;
import com.pte.rubric.internal.repository.RubricRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;
import com.pte.shared.audit.AuditLogService;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.security.access.AccessDeniedException;

@Service
public class RubricReadService {

    private final RubricRevisionRepository repository;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    @Autowired
    public RubricReadService(RubricRevisionRepository repository, ObjectMapper objectMapper,
            AuditLogService auditLogService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    /** Compatibility constructor for focused unit tests. */
    public RubricReadService(RubricRevisionRepository repository) {
        this(repository, JsonMapper.builder().build(), null);
    }

    @Transactional(readOnly = true)
    public PagedResult<RubricResponse> list(int page, int size, String status, String taskTypeKey,
            String rubricKey, UUID authorUserPublicId, Integer version, CurrentUser caller) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        RubricStatus parsedStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                parsedStatus = RubricStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new com.pte.rubric.internal.exception.RubricValidationException(
                        com.pte.rubric.internal.constant.RubricConstants.RUBRIC_INVALID_STATUS);
            }
        }
        boolean staffOnly = caller != null && caller.hasRole(SecurityRoles.ACADEMIC_STAFF)
                && !caller.hasRole(SecurityRoles.ACADEMIC_MANAGER) && !caller.hasRole(SecurityRoles.PLATFORM_ADMIN);
        String normalizedTaskTypeKey = taskTypeKey == null || taskTypeKey.isBlank()
                ? null : taskTypeKey.trim().toUpperCase(Locale.ROOT);
        String normalizedRubricKey = rubricKey == null || rubricKey.isBlank()
                ? null : "%" + rubricKey.trim().toUpperCase(Locale.ROOT) + "%";
        var result = repository.findPage(parsedStatus, normalizedTaskTypeKey, normalizedRubricKey, authorUserPublicId, version,
                staffOnly, caller == null ? null : caller.userId(),
                PageRequest.of(Math.max(page, 0), boundedSize));
        var rows = result.getContent().stream().map(RubricMapper::toResponse).toList();
        return new PagedResult<>(rows, new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast(), result.hasNext(), result.hasPrevious()));
    }

    @Transactional(readOnly = true)
    public RubricResponse get(UUID publicId, CurrentUser caller) {
        return RubricMapper.toResponse(readableRevision(publicId, caller));
    }

    @Transactional(readOnly = true)
    public RubricDiffResponse diff(UUID fromPublicId, UUID toPublicId) {
        return diff(fromPublicId, toPublicId, null);
    }

    @Transactional(readOnly = true)
    public RubricDiffResponse diff(UUID fromPublicId, UUID toPublicId, CurrentUser caller) {
        RubricRevision from = readableRevision(fromPublicId, caller);
        RubricRevision to = readableRevision(toPublicId, caller);
        List<String> paths = new ArrayList<>();
        try {
            compare(objectMapper.readTree(from.getDocumentJson()), objectMapper.readTree(to.getDocumentJson()), "$", paths);
        } catch (Exception ex) {
            paths.add("$");
        }
        return new RubricDiffResponse(from.getPublicId(), from.getVersion(), to.getPublicId(), to.getVersion(),
                !java.util.Objects.equals(from.getCanonicalHash(), to.getCanonicalHash()), List.copyOf(paths));
    }

    @Transactional(readOnly = true)
    public RubricPublishedProjection resolvePublishedForTask(String taskTypeKey) {
        RubricRevision revision = repository.findWithDetailsByTaskTypeKeyAndChannelAndStatus(
                        taskTypeKey.trim().toUpperCase(Locale.ROOT), RubricChannel.PRACTICE, RubricStatus.PUBLISHED)
                .orElseThrow(RubricNotFoundException::new);
        if (revision.getRuntimeCompatibilityHash() == null || revision.getCanonicalHash() == null) {
            throw new RubricNotFoundException();
        }
        return projection(revision);
    }

    @Transactional(readOnly = true)
    public RubricPublishedProjection resolvePinned(UUID publicId) {
        RubricRevision revision = find(publicId);
        if ((revision.getStatus() != RubricStatus.PUBLISHED && revision.getStatus() != RubricStatus.RETIRED)
                || revision.getChannel() != RubricChannel.PRACTICE) {
            throw new RubricNotFoundException();
        }
        if (revision.getRuntimeCompatibilityHash() == null || revision.getCanonicalHash() == null) {
            throw new RubricNotFoundException();
        }
        return projection(revision);
    }

    /** Applies the same staff ownership rule as detail reads to secondary views. */
    @Transactional(readOnly = true)
    public void requireReadable(UUID publicId, CurrentUser caller) {
        readableRevision(publicId, caller);
    }

    RubricRevision find(UUID publicId) {
        return repository.findWithDetailsByPublicId(publicId).orElseThrow(RubricNotFoundException::new);
    }

    private RubricRevision readableRevision(UUID publicId, CurrentUser caller) {
        RubricRevision revision = find(publicId);
        if (caller != null && caller.hasRole(SecurityRoles.ACADEMIC_STAFF)
                && !caller.hasRole(SecurityRoles.ACADEMIC_MANAGER) && !caller.hasRole(SecurityRoles.PLATFORM_ADMIN)
                && (revision.getStatus() == RubricStatus.DRAFT || revision.getStatus() == RubricStatus.PENDING_APPROVAL)
                && !java.util.Objects.equals(caller.userId(), revision.getAuthorUserPublicId())) {
            if (auditLogService != null) {
                auditLogService.recordFailure(caller, "PRACTICE_RUBRIC", publicId.toString(),
                        "AUTHORIZATION_DENIED", "Rubric is not available to this author");
            }
            throw new AccessDeniedException("Rubric is not available to this author");
        }
        return revision;
    }

    private void compare(JsonNode left, JsonNode right, String path, List<String> paths) {
        if (left == null || right == null || left.isValueNode() || right.isValueNode()) {
            if (!java.util.Objects.equals(left, right)) paths.add(path);
            return;
        }
        if (left.isArray() || right.isArray()) {
            if (!left.equals(right)) paths.add(path);
            return;
        }
        java.util.Set<String> keys = new java.util.TreeSet<>();
        left.propertyNames().forEach(keys::add);
        right.propertyNames().forEach(keys::add);
        for (String key : keys) compare(left.get(key), right.get(key), path + "." + key, paths);
    }

    private RubricPublishedProjection projection(RubricRevision revision) {
        return new RubricPublishedProjection(revision.getPublicId(), revision.getRubricKey(),
                revision.getTaskTypeKey(), revision.getVersion(), revision.getCanonicalHash(),
                safePracticeDocument(revision.getDocumentJson()), revision.getRuntimeCompatibilityHash());
    }

    private String safePracticeDocument(String documentJson) {
        try {
            JsonNode node = objectMapper.readTree(documentJson);
            if (node instanceof ObjectNode object) {
                object.remove(java.util.List.of("references", "provenance"));
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new RubricNotFoundException();
        }
    }
}
