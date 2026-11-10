package com.pte.scoring.internal.rubric.workflow;

import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.domain.ScoringRubricActivationAudit;
import com.pte.scoring.internal.rubric.repository.*;
import com.pte.scoring.internal.rubric.service.*;
import com.pte.shared.security.CurrentUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.*;
import java.util.*;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.*;
import static org.mockito.Mockito.*;

public class RubricWorkflowFixture {
    public static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    public final ScoringRubricVersionRepository versions = mock(ScoringRubricVersionRepository.class);
    public final ScoringRubricActivationAuditRepository audits = mock(ScoringRubricActivationAuditRepository.class);
    public final Map<UUID, ScoringRubricVersion> rows = new LinkedHashMap<>();
    public final List<ScoringRubricActivationAudit> auditRows = new ArrayList<>();
    public final ScoringRubricVersion active = add(RubricVersionStatus.ACTIVE);
    public final RubricParametersValidator validator = new RubricParametersValidator(CATALOG, CODEC);
    public final RubricVersionService lifecycle;
    public final RubricDiffService diffs;
    public final RubricActivationWorkflow activation;
    public final RubricVersionCommandService commands;

    public RubricWorkflowFixture() {
        when(versions.findByPublicId(any())).thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0))));
        when(versions.findByStatus(RubricVersionStatus.ACTIVE)).thenAnswer(call -> currentActive());
        when(versions.lockActive(RubricVersionStatus.ACTIVE)).thenAnswer(call -> currentActive());
        when(versions.findPublicIdByStatus(RubricVersionStatus.ACTIVE)).thenAnswer(call -> currentActive().map(ScoringRubricVersion::getPublicId));
        when(versions.findMaxVersionNo()).thenAnswer(call -> rows.size());
        when(versions.save(any())).thenAnswer(call -> persist(call.getArgument(0)));
        when(versions.saveAndFlush(any())).thenAnswer(call -> persist(call.getArgument(0)));
        when(versions.retireActive(any())).thenAnswer(call -> {
            var old = currentActive().orElseThrow();
            ReflectionTestUtils.setField(old, "status", RubricVersionStatus.RETIRED);
            return 1;
        });
        when(audits.save(any())).thenAnswer(call -> { auditRows.add(call.getArgument(0)); return call.getArgument(0); });
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        lifecycle = new RubricVersionService(versions, audits, validator, Clock.fixed(NOW, ZoneOffset.UTC));
        diffs = new RubricDiffService(versions);
        activation = new RubricActivationWorkflow(versions, lifecycle, diffs, manager);
        commands = new RubricVersionCommandService(versions, lifecycle, validator, activation);
    }

    public ScoringRubricVersion add(RubricVersionStatus status) {
        var row = version(status, seedJson());
        rows.put(row.getPublicId(), row);
        return row;
    }

    public CurrentUser caller(String role) { return new CurrentUser(UUID.randomUUID(), null, List.of(role)); }

    private Optional<ScoringRubricVersion> currentActive() {
        return rows.values().stream().filter(row -> row.getStatus() == RubricVersionStatus.ACTIVE).findFirst();
    }

    private ScoringRubricVersion persist(ScoringRubricVersion row) {
        if (row.getPublicId() == null) row.setPublicId(UUID.randomUUID());
        rows.put(row.getPublicId(), row);
        return row;
    }
}
