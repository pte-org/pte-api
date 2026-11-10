package com.pte.itembank.internal.generation.prompt.version;

import com.pte.itembank.internal.generation.domain.AiPromptStatus;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A prompt has at most one proposal for changing it: the newest version that is newer than the one in use and has not
 * been accepted. A rejected proposal stays the proposal until someone edits it again or drops it.
 */
final class PromptProposals {

    static final Set<AiPromptStatus> OPEN = EnumSet.of(AiPromptStatus.DRAFT, AiPromptStatus.PENDING,
            AiPromptStatus.REJECTED);

    private PromptProposals() {
    }

    static Optional<AiPromptVersion> openProposal(List<AiPromptVersion> newestFirst, AiPromptVersion live) {
        return newestFirst.stream()
                .filter(version -> OPEN.contains(version.getStatus()) && version.getVersionNo() > live.getVersionNo())
                .findFirst();
    }
}
