package com.pte.practice.internal.service;

import com.pte.identity.IdentityTokenResponse;
import com.pte.identity.IdentityTokenService;
import com.pte.identity.PracticeIdentityStatus;
import com.pte.identity.PracticeIdentityService;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.practice.PracticeObservability;
import com.pte.practice.internal.domain.PracticeEmailChallenge;
import com.pte.practice.internal.dto.request.PracticeEmailRequest;
import com.pte.practice.internal.dto.request.PracticeVerifyRequest;
import com.pte.practice.internal.event.PracticeEmailChallengeRequestedEvent;
import com.pte.practice.internal.exception.InvalidPracticeChallengeException;
import com.pte.practice.internal.repository.PracticeEmailChallengeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeEmailAuthServiceTest {

    @Mock
    private PracticeIdentityService identityService;
    @Mock
    private PracticeEmailChallengeRepository challengeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private IdentityTokenService identityTokenService;
    @Mock
    private PracticeObservability observability;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void requestReturnsChallengeWithoutReturningTheCode() {
        PracticeEmailAuthService service = newService();
        PracticeIdentityService.IdentityHandle identity = new PracticeIdentityService.IdentityHandle(
                11L, UUID.randomUUID(), "email-hash", PracticeIdentityStatus.ACTIVE);
        UUID challengeId = UUID.randomUUID();
        when(identityService.getOrCreate("student@example.com")).thenReturn(identity);
        when(challengeRepository.countByIdentityIdAndCreatedAtAfterAndDeletedFalse(any(), any())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-code");
        when(challengeRepository.save(any(PracticeEmailChallenge.class))).thenAnswer(invocation -> {
            PracticeEmailChallenge challenge = invocation.getArgument(0);
            challenge.setPublicId(challengeId);
            return challenge;
        });

        var result = service.requestChallenge(new PracticeEmailRequest(" Student@Example.com "));

        assertThat(result.challengeId()).isEqualTo(challengeId);
        ArgumentCaptor<PracticeEmailChallengeRequestedEvent> event =
                ArgumentCaptor.forClass(PracticeEmailChallengeRequestedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().recipientEmail()).isEqualTo("student@example.com");
        assertThat(event.getValue().body()).matches("Your PTE Practice verification code is \\d{6}\\..*");
    }

    @Test
    void verifyConsumesChallengeAndIssuesCanonicalTokens() {
        PracticeEmailAuthService service = newService();
        UUID challengeId = UUID.randomUUID();
        UUID shellUserId = UUID.randomUUID();
        PracticeIdentityService.IdentityHandle identity = new PracticeIdentityService.IdentityHandle(
                11L, UUID.randomUUID(), "email-hash", PracticeIdentityStatus.ACTIVE);
        PracticeEmailChallenge challenge = challenge(challengeId, 11L);
        User shellUser = new User();
        shellUser.setPublicId(shellUserId);
        shellUser.setUsername("practice.shell");
        shellUser.setRoles(Set.of(Role.STUDENT));
        IdentityTokenResponse tokens = new IdentityTokenResponse("access", "refresh", "Bearer", 900L, false);
        when(challengeRepository.findWithLockByPublicIdAndDeletedFalse(challengeId))
                .thenReturn(Optional.of(challenge));
        when(identityService.findById(11L)).thenReturn(Optional.of(identity));
        when(identityService.matchesEmail(identity, "student@example.com")).thenReturn(true);
        when(passwordEncoder.matches("123456", "hashed-code")).thenReturn(true);
        when(identityService.provisionVerifiedUser(11L, "student@example.com")).thenReturn(shellUser);
        when(identityTokenService.issueForVerifiedUser(shellUserId)).thenReturn(tokens);

        IdentityTokenResponse result = service.verifyChallenge(
                new PracticeVerifyRequest(challengeId, "STUDENT@example.com", "123456"));

        assertThat(result).isEqualTo(tokens);
        assertThat(challenge.getConsumedAt()).isNotNull();
        verify(challengeRepository).save(challenge);
    }

    @Test
    void wrongCodeConsumesAnAttemptAndNeverIssuesTokens() {
        PracticeEmailAuthService service = newService();
        UUID challengeId = UUID.randomUUID();
        PracticeIdentityService.IdentityHandle identity = new PracticeIdentityService.IdentityHandle(
                11L, UUID.randomUUID(), "email-hash", PracticeIdentityStatus.ACTIVE);
        PracticeEmailChallenge challenge = challenge(challengeId, 11L);
        when(challengeRepository.findWithLockByPublicIdAndDeletedFalse(challengeId))
                .thenReturn(Optional.of(challenge));
        when(identityService.findById(11L)).thenReturn(Optional.of(identity));
        when(identityService.matchesEmail(identity, "student@example.com")).thenReturn(true);
        when(passwordEncoder.matches("000000", "hashed-code")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyChallenge(
                new PracticeVerifyRequest(challengeId, "student@example.com", "000000")))
                .isInstanceOf(InvalidPracticeChallengeException.class);

        assertThat(challenge.getFailedAttempts()).isEqualTo(1);
        verify(challengeRepository).save(challenge);
    }

    @Test
    void consumedChallengeCannotBeReplayed() {
        PracticeEmailAuthService service = newService();
        UUID challengeId = UUID.randomUUID();
        PracticeIdentityService.IdentityHandle identity = new PracticeIdentityService.IdentityHandle(
                11L, UUID.randomUUID(), "email-hash", PracticeIdentityStatus.ACTIVE);
        PracticeEmailChallenge challenge = challenge(challengeId, 11L);
        challenge.setConsumedAt(Instant.now());
        when(challengeRepository.findWithLockByPublicIdAndDeletedFalse(challengeId))
                .thenReturn(Optional.of(challenge));
        when(identityService.findById(11L)).thenReturn(Optional.of(identity));
        when(identityService.matchesEmail(identity, "student@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.verifyChallenge(
                new PracticeVerifyRequest(challengeId, "student@example.com", "123456")))
                .isInstanceOf(InvalidPracticeChallengeException.class);
    }

    @Test
    void revokedIdentityCannotVerifyAnOutstandingChallenge() {
        PracticeEmailAuthService service = newService();
        UUID challengeId = UUID.randomUUID();
        PracticeIdentityService.IdentityHandle identity = new PracticeIdentityService.IdentityHandle(
                11L, UUID.randomUUID(), "email-hash", PracticeIdentityStatus.REVOKED);
        PracticeEmailChallenge challenge = challenge(challengeId, 11L);
        when(challengeRepository.findWithLockByPublicIdAndDeletedFalse(challengeId))
                .thenReturn(Optional.of(challenge));
        when(identityService.findById(11L)).thenReturn(Optional.of(identity));

        assertThatThrownBy(() -> service.verifyChallenge(
                new PracticeVerifyRequest(challengeId, "student@example.com", "123456")))
                .isInstanceOf(InvalidPracticeChallengeException.class);
    }

    private PracticeEmailAuthService newService() {
        return new PracticeEmailAuthService(identityService, challengeRepository, passwordEncoder,
                identityTokenService, observability, eventPublisher);
    }

    private PracticeEmailChallenge challenge(UUID publicId, Long identityId) {
        PracticeEmailChallenge challenge = new PracticeEmailChallenge();
        challenge.setPublicId(publicId);
        challenge.setIdentityId(identityId);
        challenge.setCodeHash("hashed-code");
        challenge.setExpiresAt(Instant.now().plusSeconds(60));
        return challenge;
    }
}
