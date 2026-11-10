package com.pte.itembank.internal.generation.prompt.version;

import com.pte.itembank.internal.generation.constant.PromptVersionConstants;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.domain.AiPromptStatus;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;
import com.pte.itembank.internal.generation.exception.AiPromptException;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptProposalRequest;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptStatusRequest;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptStatusRequest.Action;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptVersionResponse;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static com.pte.itembank.internal.generation.prompt.version.PromptVersionTestKit.admin;
import static com.pte.itembank.internal.generation.prompt.version.PromptVersionTestKit.manager;
import static com.pte.itembank.internal.generation.prompt.version.PromptVersionTestKit.staff;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiPromptVersionCommandServiceTest {

    private static final String CATEGORY = "GENERATION";
    private static final String KEY = "READ_ALOUD";

    private final PromptVersionTestKit kit = new PromptVersionTestKit();

    private PromptVersionResponse proposeBy(CurrentUser author) {
        return kit.propose(CATEGORY, KEY, "A new line.", author);
    }

    private AiPromptVersion row(PromptVersionResponse response) {
        return kit.byId(response.publicId()).orElseThrow();
    }

    private PromptVersionResponse change(PromptVersionResponse version, Action action, CurrentUser caller) {
        return kit.commands.changeStatus(version.publicId(), new PromptStatusRequest(action, "reason"), caller);
    }

    private void passTest(PromptVersionResponse version, CurrentUser tester) {
        row(version).recordTest(true, "ok", tester.userId(), PromptVersionTestKit.CLOCK.instant());
    }

    @Test
    void theFirstSaveStartsAProposalThatTakesTheNextVersionNumber() {
        PromptVersionResponse proposal = proposeBy(staff());

        assertThat(proposal.status()).isEqualTo("DRAFT");
        assertThat(proposal.versionNo()).isEqualTo(2);
        assertThat(proposal.assembledText()).contains("A new line.");
        assertThat(proposal.test()).isNull();
        assertThat(kit.live(AiPromptKind.GENERATOR, KEY).getVersionNo()).isEqualTo(1);
    }

    @Test
    void savingAgainEditsTheSameProposalInsteadOfStartingAnother() {
        CurrentUser author = staff();
        PromptVersionResponse first = proposeBy(author);

        PromptVersionResponse second = kit.commands.saveProposal(CATEGORY, KEY,
                new PromptProposalRequest(kit.changed(AiPromptKind.GENERATOR, KEY, "Other line."),
                        first.lockVersion()), author);

        assertThat(second.publicId()).isEqualTo(first.publicId());
        assertThat(second.assembledText()).contains("Other line.").doesNotContain("A new line.");
    }

    @Test
    void aProposalThatChangesNothingIsRefused() {
        PromptVersionResponse live = kit.mapper.toResponse(kit.live(AiPromptKind.GENERATOR, KEY));

        assertThatThrownBy(() -> kit.commands.saveProposal(CATEGORY, KEY,
                new PromptProposalRequest(live.content(), null), staff())).isInstanceOfSatisfying(
                AiPromptException.class, failure ->
                        assertThat(failure.getCode()).isEqualTo(PromptVersionConstants.NO_CHANGE));
    }

    @Test
    void aSavedTestStopsCountingAfterTheContentChanges() {
        CurrentUser author = staff();
        PromptVersionResponse proposal = proposeBy(author);
        passTest(proposal, author);
        assertThat(row(proposal).hasPassingTest()).isTrue();

        PromptVersionResponse saved = kit.commands.saveProposal(CATEGORY, KEY,
                new PromptProposalRequest(kit.changed(AiPromptKind.GENERATOR, KEY, "Changed again."), null), author);

        assertThat(saved.test().current()).isFalse();
        assertThat(row(saved).hasPassingTest()).isFalse();
    }

    @Test
    void aLostUpdateIsRefused() {
        CurrentUser author = staff();
        PromptVersionResponse proposal = proposeBy(author);

        assertThatThrownBy(() -> kit.commands.saveProposal(CATEGORY, KEY, new PromptProposalRequest(
                kit.changed(AiPromptKind.GENERATOR, KEY, "x"), proposal.lockVersion() + 1), author))
                .isInstanceOfSatisfying(AiPromptException.class, failure ->
                        assertThat(failure.getCode()).isEqualTo(PromptVersionConstants.CONCURRENT_CHANGE));
    }

    @Test
    void anotherStaffMemberCannotEditSomeoneElsesProposalButAManagerCan() {
        proposeBy(staff());

        assertThatThrownBy(() -> proposeBy(staff())).isInstanceOfSatisfying(AiPromptException.class,
                failure -> assertThat(failure.getCode()).isEqualTo(PromptVersionConstants.AUTHOR_ONLY));
        assertThat(proposeBy(manager()).versionNo()).isEqualTo(2);
    }

    @Test
    void aProposalWaitingForApprovalCannotBeEdited() {
        CurrentUser author = staff();
        PromptVersionResponse proposal = proposeBy(author);
        passTest(proposal, author);
        change(proposal, Action.SUBMIT, author);

        assertThatThrownBy(() -> proposeBy(author)).isInstanceOfSatisfying(AiPromptException.class,
                failure -> assertThat(failure.getCode()).isEqualTo(PromptVersionConstants.NOT_EDITABLE));
    }

    @Test
    void sendingForApprovalNeedsAPassingTestUnlessTheSenderIsAnAdmin() {
        CurrentUser author = staff();
        PromptVersionResponse proposal = proposeBy(author);

        assertThatThrownBy(() -> change(proposal, Action.SUBMIT, author)).isInstanceOfSatisfying(
                AiPromptException.class,
                failure -> assertThat(failure.getCode()).isEqualTo(PromptVersionConstants.TEST_REQUIRED));

        passTest(proposal, author);
        assertThat(change(proposal, Action.SUBMIT, author).status()).isEqualTo("PENDING");
    }

    @Test
    void anAdminMayApplyAProposalWithoutATest() {
        PromptVersionResponse proposal = proposeBy(admin());

        assertThat(change(proposal, Action.APPROVE, admin()).status()).isEqualTo("ACTIVE");
    }

    @Test
    void acceptingReplacesTheVersionInUseAndTheNextSaveStartsANewProposal() {
        CurrentUser author = staff();
        AiPromptVersion before = kit.live(AiPromptKind.GENERATOR, KEY);
        PromptVersionResponse proposal = proposeBy(author);
        passTest(proposal, author);
        change(proposal, Action.SUBMIT, author);

        PromptVersionResponse accepted = change(proposal, Action.APPROVE, manager());

        assertThat(accepted.status()).isEqualTo("ACTIVE");
        assertThat(before.getStatus()).isEqualTo(AiPromptStatus.RETIRED);
        assertThat(kit.live(AiPromptKind.GENERATOR, KEY).getPublicId()).isEqualTo(proposal.publicId());
        assertThat(kit.propose(CATEGORY, KEY, "Next change.", author).versionNo()).isEqualTo(3);
    }

    @Test
    void aManagerCannotAcceptTheirOwnProposalButAnAdminCan() {
        CurrentUser manager = manager();
        PromptVersionResponse proposal = proposeBy(manager);
        passTest(proposal, manager);
        change(proposal, Action.SUBMIT, manager);

        assertThatThrownBy(() -> change(proposal, Action.APPROVE, manager)).isInstanceOfSatisfying(
                AiPromptException.class, failure ->
                        assertThat(failure.getCode()).isEqualTo(PromptVersionConstants.SELF_APPROVAL_FORBIDDEN));
        assertThat(change(proposal, Action.APPROVE, admin()).status()).isEqualTo("ACTIVE");
    }

    @Test
    void staffCannotAcceptOrReturn() {
        CurrentUser author = staff();
        PromptVersionResponse proposal = proposeBy(author);
        passTest(proposal, author);
        change(proposal, Action.SUBMIT, author);

        assertThatThrownBy(() -> change(proposal, Action.APPROVE, staff())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> change(proposal, Action.REJECT, staff())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void returningNeedsAReasonAndEditingAReturnedProposalReopensIt() {
        CurrentUser author = staff();
        PromptVersionResponse proposal = proposeBy(author);
        passTest(proposal, author);
        change(proposal, Action.SUBMIT, author);

        assertThatThrownBy(() -> kit.commands.changeStatus(proposal.publicId(),
                new PromptStatusRequest(Action.REJECT, " "), manager())).isInstanceOfSatisfying(
                AiPromptException.class, failure ->
                        assertThat(failure.getCode()).isEqualTo(PromptVersionConstants.REASON_REQUIRED));

        PromptVersionResponse returned = change(proposal, Action.REJECT, manager());
        assertThat(returned.status()).isEqualTo("REJECTED");
        assertThat(returned.rejectionReason()).isEqualTo("reason");

        PromptVersionResponse again = kit.propose(CATEGORY, KEY, "Fixed.", author);
        assertThat(again.publicId()).isEqualTo(proposal.publicId());
        assertThat(again.status()).isEqualTo("DRAFT");
        assertThat(again.rejectionReason()).isNull();
    }

    @Test
    void droppingAProposalLetsTheNextSaveStartAFreshOneWithoutReusingTheNumber() {
        CurrentUser author = staff();
        PromptVersionResponse proposal = proposeBy(author);
        change(proposal, Action.DISCARD, author);

        assertThat(proposeBy(author).versionNo()).isEqualTo(3);
    }
}
