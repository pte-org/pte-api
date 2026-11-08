package com.pte.itembank.internal.generation.prompt.version;

import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;
import com.pte.itembank.internal.generation.prompt.PromptContent;
import com.pte.itembank.internal.generation.prompt.PromptContentCodec;
import com.pte.itembank.internal.generation.prompt.PromptTextAssembler;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptTestInfo;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptVersionResponse;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptVersionSummary;
import org.springframework.stereotype.Component;

/** Turns a stored version into what the API returns. */
@Component
class PromptVersionMapper {

    private final PromptContentCodec codec;
    private final PromptUserNames names;

    PromptVersionMapper(PromptContentCodec codec, PromptUserNames names) {
        this.codec = codec;
        this.names = names;
    }

    PromptVersionResponse toResponse(AiPromptVersion version) {
        PromptContent content = codec.read(version.getContent());
        boolean titled = version.getKind() == AiPromptKind.GENERATOR || version.getKind() == AiPromptKind.SHARED;
        String assembled = titled ? PromptTextAssembler.assemble(content)
                : PromptTextAssembler.assembleWithoutTitle(content);
        return new PromptVersionResponse(version.getPublicId(), PromptCategories.categoryOf(version.getKind()),
                version.getPromptKey(), version.getVersionNo(), version.getStatus().name(), content, assembled,
                PromptTextAssembler.placeholdersOf(assembled), names.nameOf(version.getAuthorUserPublicId()),
                version.getAuthorUserPublicId(), names.nameOf(version.getApproverUserPublicId()),
                version.getRejectionReason(), version.getCreatedAt(), version.getSubmittedAt(),
                version.getDecidedAt(), version.getActivatedAt(), version.getLockVersion(), testInfo(version));
    }

    PromptVersionSummary toSummary(AiPromptVersion version) {
        return new PromptVersionSummary(version.getPublicId(), version.getVersionNo(), version.getStatus().name(),
                names.nameOf(version.getAuthorUserPublicId()), names.nameOf(version.getApproverUserPublicId()),
                version.getCreatedAt(), version.getSubmittedAt(), version.getActivatedAt(),
                version.hasPassingTest());
    }

    private PromptTestInfo testInfo(AiPromptVersion version) {
        if (version.getTestedAt() == null) {
            return null;
        }
        return new PromptTestInfo(Boolean.TRUE.equals(version.getTestPassed()),
                version.getContentHash().equals(version.getTestedContentHash()), version.getTestNote(),
                version.getTestedAt(), names.nameOf(version.getTestedByUserPublicId()));
    }
}
