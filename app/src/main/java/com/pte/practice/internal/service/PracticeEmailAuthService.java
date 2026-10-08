package com.pte.practice.internal.service;

import com.pte.identity.IdentityTokenResponse;
import com.pte.identity.IdentityTokenService;
import com.pte.identity.PracticeIdentityStatus;
import com.pte.identity.PracticeIdentityService;
import com.pte.practice.PracticeObservability;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeEmailChallenge;
import com.pte.practice.internal.dto.request.PracticeEmailRequest;
import com.pte.practice.internal.dto.request.PracticeVerifyRequest;
import com.pte.practice.internal.dto.response.PracticeChallengeResponse;
import com.pte.practice.internal.event.PracticeEmailChallengeRequestedEvent;
import com.pte.practice.internal.exception.InvalidPracticeChallengeException;
import com.pte.practice.internal.exception.PracticeChallengeRateLimitException;
import com.pte.practice.internal.repository.PracticeEmailChallengeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Passwordless email challenge orchestration for the isolated practice app. */
@Service
public class PracticeEmailAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PracticeIdentityService identityService;
    private final PracticeEmailChallengeRepository challengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityTokenService identityTokenService;
    private final PracticeObservability observability;
    private final ApplicationEventPublisher eventPublisher;

    public PracticeEmailAuthService(PracticeIdentityService identityService,
            PracticeEmailChallengeRepository challengeRepository, PasswordEncoder passwordEncoder,
            IdentityTokenService identityTokenService, PracticeObservability observability,
            ApplicationEventPublisher eventPublisher) {
        this.identityService = identityService;
        this.challengeRepository = challengeRepository;
        this.passwordEncoder = passwordEncoder;
        this.identityTokenService = identityTokenService;
        this.observability = observability;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PracticeChallengeResponse requestChallenge(PracticeEmailRequest request) {
        String email = normalizeEmail(request.email());
        PracticeIdentityService.IdentityHandle identity = identityService.getOrCreate(email);
        Instant now = Instant.now();
        Instant windowStart = now.minus(PracticeConstants.CHALLENGE_REQUEST_WINDOW_MINUTES, ChronoUnit.MINUTES);
        long recentRequests = challengeRepository.countByIdentityIdAndCreatedAtAfterAndDeletedFalse(
                identity.internalId(), windowStart);
        if (recentRequests >= PracticeConstants.MAX_CHALLENGE_REQUESTS_PER_WINDOW) {
            observability.challengeRateLimited();
            throw new PracticeChallengeRateLimitException();
        }

        String code = nextCode();
        PracticeEmailChallenge challenge = new PracticeEmailChallenge();
        challenge.setIdentityId(identity.internalId());
        challenge.setCodeHash(passwordEncoder.encode(code));
        challenge.setExpiresAt(now.plus(PracticeConstants.VERIFICATION_CODE_TTL_MINUTES, ChronoUnit.MINUTES));
        PracticeEmailChallenge saved = challengeRepository.save(challenge);
        observability.challengeRequested();

        String body = PracticeConstants.PRACTICE_EMAIL_BODY_TEMPLATE.formatted(
                code, PracticeConstants.VERIFICATION_CODE_TTL_MINUTES);
        eventPublisher.publishEvent(new PracticeEmailChallengeRequestedEvent(
                email, saved.getPublicId(), PracticeConstants.PRACTICE_EMAIL_SUBJECT, body));
        return new PracticeChallengeResponse(saved.getPublicId(), saved.getExpiresAt());
    }

    @Transactional
    public IdentityTokenResponse verifyChallenge(PracticeVerifyRequest request) {
        try {
            String email = normalizeEmail(request.email());
            PracticeEmailChallenge challenge = challengeRepository
                    .findWithLockByPublicIdAndDeletedFalse(request.challengeId())
                    .orElseThrow(InvalidPracticeChallengeException::new);
            PracticeIdentityService.IdentityHandle identity = identityService.findById(challenge.getIdentityId())
                    .filter(candidate -> candidate.status() == PracticeIdentityStatus.ACTIVE)
                    .filter(candidate -> identityService.matchesEmail(candidate, email))
                    .orElseThrow(InvalidPracticeChallengeException::new);

            Instant now = Instant.now();
            if (challenge.isConsumedOrExpired(now)) {
                throw new InvalidPracticeChallengeException();
            }
            if (!passwordEncoder.matches(request.code(), challenge.getCodeHash())) {
                challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
                challengeRepository.save(challenge);
                throw new InvalidPracticeChallengeException();
            }

            challenge.setConsumedAt(now);
            challengeRepository.save(challenge);
            var shellUser = identityService.provisionVerifiedUser(identity.internalId(), email);
            observability.challengeVerified();
            return identityTokenService.issueForVerifiedUser(shellUser.getPublicId());
        } catch (InvalidPracticeChallengeException ex) {
            observability.challengeRejected();
            throw ex;
        }
    }

    private String nextCode() {
        return String.format(Locale.ROOT, "%0" + PracticeConstants.VERIFICATION_CODE_LENGTH + "d",
                RANDOM.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
