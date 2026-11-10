package com.pte.itembank.internal.generation.prompt.version;

import com.pte.identity.IdentityService;
import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.internal.generation.prompt.PromptContent;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptProposalRequest;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptVersionResponse;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.domain.AiPromptStatus;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;
import com.pte.itembank.internal.generation.prompt.PromptContentCodec;
import com.pte.itembank.internal.generation.prompt.PromptTestRegistry;
import com.pte.itembank.internal.generation.repository.AiPromptVersionRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The pieces of the prompt-editing services over an in-memory store holding the shipped prompts. */
final class PromptVersionTestKit {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"), ZoneOffset.UTC);
    static final PromptContentCodec CODEC = PromptTestRegistry.codec();

    final List<AiPromptVersion> rows = new ArrayList<>(PromptTestRegistry.seededVersions());
    final AiPromptVersionRepository repository = mock(AiPromptVersionRepository.class);
    final PromptVersionMapper mapper = new PromptVersionMapper(CODEC, new PromptUserNames(mock(IdentityService.class)));
    final AiPromptVersionCommandService commands;

    PromptVersionTestKit() {
        visible().forEach(row -> row.setPublicId(UUID.randomUUID()));
        when(repository.findByPublicId(any())).thenAnswer(call -> visible()
                .filter(row -> row.getPublicId().equals(call.getArgument(0))).findFirst());
        when(repository.findByKindAndStatus(any(), any())).thenAnswer(call -> visible()
                .filter(row -> row.getKind() == call.getArgument(0) && row.getStatus() == call.getArgument(1))
                .toList());
        when(repository.findByKindAndPromptKeyOrderByVersionNoDesc(any(), any())).thenAnswer(call -> visible()
                .filter(row -> row.getKind() == call.getArgument(0) && row.getPromptKey().equals(call.getArgument(1)))
                .sorted(Comparator.comparingInt(AiPromptVersion::getVersionNo).reversed()).toList());
        when(repository.highestVersionNo(any(), any())).thenAnswer(call -> rows.stream()
                .filter(row -> row.getKind().name().equals(call.getArgument(0))
                        && row.getPromptKey().equals(call.getArgument(1)))
                .mapToInt(AiPromptVersion::getVersionNo).max().orElse(0));
        when(repository.retireActive(any(), any(), any())).thenAnswer(call -> {
            int retired = 0;
            for (AiPromptVersion row : rows) {
                if (row.getKind() == call.getArgument(0) && row.getPromptKey().equals(call.getArgument(1))
                        && row.getStatus() == AiPromptStatus.ACTIVE) {
                    row.retire(call.getArgument(2));
                    retired++;
                }
            }
            return retired;
        });
        when(repository.saveAndFlush(any(AiPromptVersion.class))).thenAnswer(call -> {
            AiPromptVersion saved = call.getArgument(0);
            if (saved.getPublicId() == null) {
                saved.setPublicId(UUID.randomUUID());
            }
            if (!rows.contains(saved)) {
                rows.add(saved);
            }
            return saved;
        });
        commands = new AiPromptVersionCommandService(repository, CODEC, new PromptContentValidator(), mapper, CLOCK);
    }

    private java.util.stream.Stream<AiPromptVersion> visible() {
        return rows.stream().filter(row -> !row.isDeleted());
    }

    /** The live content with one more line in its opening: a change that is valid for any prompt. */
    PromptContent changed(AiPromptKind kind, String key, String extraLine) {
        PromptContent live = CODEC.read(live(kind, key).getContent());
        List<Block> intro = new ArrayList<>(live.intro());
        intro.add(Block.text("", extraLine));
        return new PromptContent(live.title(), intro, live.sections());
    }

    PromptVersionResponse propose(String category, String key, String extraLine, CurrentUser caller) {
        AiPromptKind kind = switch (category) {
            case "SHARED" -> AiPromptKind.SHARED;
            case "REVIEW" -> AiPromptKind.REVIEWER;
            case "SCORING" -> AiPromptKind.SCORER;
            default -> AiPromptKind.GENERATOR;
        };
        return commands.saveProposal(category, key, new PromptProposalRequest(changed(kind, key, extraLine), null), caller);
    }

    AiPromptVersion live(AiPromptKind kind, String key) {
        return visible().filter(row -> row.getKind() == kind && row.getPromptKey().equals(key)
                && row.getStatus() == AiPromptStatus.ACTIVE).findFirst().orElseThrow();
    }

    Optional<AiPromptVersion> byId(UUID publicId) {
        return visible().filter(row -> row.getPublicId().equals(publicId)).findFirst();
    }

    static CurrentUser user(String role) {
        return new CurrentUser(UUID.randomUUID(), null, List.of(role));
    }

    static CurrentUser staff() {
        return user(SecurityRoles.ACADEMIC_STAFF);
    }

    static CurrentUser manager() {
        return user(SecurityRoles.ACADEMIC_MANAGER);
    }

    static CurrentUser admin() {
        return user(SecurityRoles.PLATFORM_ADMIN);
    }
}
