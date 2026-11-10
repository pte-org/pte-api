package com.pte.itembank.internal.generation.prompt.version;

import com.pte.itembank.internal.generation.constant.PromptVersionConstants;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.domain.AiPromptStatus;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;
import com.pte.itembank.internal.generation.exception.AiPromptException;
import com.pte.itembank.internal.generation.exception.GenerationPromptNotFoundException;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptVersionResponse;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptWorkspaceResponse;
import com.pte.itembank.internal.generation.repository.AiPromptVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Reads of an editable prompt: the page of one prompt, and any single version. */
@Service
public class AiPromptVersionQueryService {

    private static final Set<AiPromptStatus> ACCEPTED = Set.of(AiPromptStatus.ACTIVE, AiPromptStatus.RETIRED);

    private final AiPromptVersionRepository versions;
    private final PromptVersionMapper mapper;

    AiPromptVersionQueryService(AiPromptVersionRepository versions, PromptVersionMapper mapper) {
        this.versions = versions;
        this.mapper = mapper;
    }

    /** The version in use, its open proposal if any, and the accepted changes, newest first. */
    @Transactional(readOnly = true)
    public PromptWorkspaceResponse workspace(String category, String key) {
        AiPromptKind kind = PromptCategories.editableKind(category);
        List<AiPromptVersion> all = versions.findByKindAndPromptKeyOrderByVersionNoDesc(kind,
                PromptCategories.normalizedKey(kind, key));
        AiPromptVersion live = all.stream().filter(version -> version.getStatus() == AiPromptStatus.ACTIVE).findFirst()
                .orElseThrow(GenerationPromptNotFoundException::new);
        return new PromptWorkspaceResponse(mapper.toResponse(live),
                PromptProposals.openProposal(all, live).map(mapper::toResponse).orElse(null),
                all.stream().filter(version -> ACCEPTED.contains(version.getStatus())).map(mapper::toSummary)
                        .toList());
    }

    @Transactional(readOnly = true)
    public PromptVersionResponse get(UUID publicId) {
        return mapper.toResponse(versions.findByPublicId(publicId).orElseThrow(AiPromptVersionQueryService::notFound));
    }

    static AiPromptException notFound() {
        return new AiPromptException(HttpStatus.NOT_FOUND, PromptVersionConstants.VERSION_NOT_FOUND,
                PromptVersionConstants.VERSION_NOT_FOUND_MESSAGE);
    }
}
