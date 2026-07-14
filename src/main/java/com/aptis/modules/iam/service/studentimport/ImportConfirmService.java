package com.aptis.modules.iam.service.studentimport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.domain.Student;
import com.aptis.modules.iam.domain.enums.ImportSessionState;
import com.aptis.modules.iam.domain.studentimport.CredentialRow;
import com.aptis.modules.iam.domain.studentimport.ImportSession;
import com.aptis.modules.iam.interfaces.CredentialProvisioning;
import com.aptis.modules.iam.interfaces.StudentImportConfirmer;
import com.aptis.modules.iam.repository.StudentRepository;

@Service
public class ImportConfirmService implements StudentImportConfirmer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportConfirmService.class);

    private final ImportSessionCache importSessionCache;
    private final StudentRepository studentRepository;
    private final CredentialProvisioning credentialProvisioning;
    private final CredentialExcelWriter credentialExcelWriter;
    private final StudentUsernameSequenceService usernameSequenceService;

    public ImportConfirmService(
            ImportSessionCache importSessionCache,
            StudentRepository studentRepository,
            CredentialProvisioning credentialProvisioning,
            CredentialExcelWriter credentialExcelWriter,
            StudentUsernameSequenceService usernameSequenceService) {
        this.importSessionCache = importSessionCache;
        this.studentRepository = studentRepository;
        this.credentialProvisioning = credentialProvisioning;
        this.credentialExcelWriter = credentialExcelWriter;
        this.usernameSequenceService = usernameSequenceService;
    }

    @Override
    @Transactional
    public byte[] confirm(
            String hostId,
            Long organizationId,
            String importId) {
        ImportSession session = loadSession(hostId, organizationId, importId);
        if (!startConfirming(session)) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT);
        }

        List<String> usernames = usernameSequenceService.reserveUsernames(
                organizationId,
                session.getParsedRows().size());
        long credentialStartTime = System.currentTimeMillis();
        List<StudentCredential> studentCredentials = buildStudentCredentials(session, usernames);
        LOGGER.info(
                "Student import confirm generated credentials importId={}, rows={}, durationMs={}",
                importId,
                studentCredentials.size(),
                System.currentTimeMillis() - credentialStartTime);
        List<Student> students = studentCredentials.stream()
                .map(StudentCredential::student)
                .toList();
        List<CredentialRow> credentialRows = studentCredentials.stream()
                .map(StudentCredential::credentialRow)
                .toList();

        try {
            studentRepository.saveAll(students);
            studentRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            session.transitionState(ImportSessionState.CONFIRMING, ImportSessionState.PARSED);
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT);
        }

        byte[] excelPayload = credentialExcelWriter.write(
                session.getParsedRows(),
                session.getColumnHeaders(),
                credentialRows);
        session.transitionState(ImportSessionState.CONFIRMING, ImportSessionState.CONFIRMED);
        importSessionCache.removeSession(hostId, organizationId, importId);
        return excelPayload;
    }

    private List<StudentCredential> buildStudentCredentials(
            ImportSession session,
            List<String> usernames) {
        int workerCount = Math.min(
                IamApiConstants.IMPORT_CREDENTIAL_HASH_THREADS,
                Math.max(1, session.getParsedRows().size()));
        ExecutorService executorService = Executors.newFixedThreadPool(workerCount);
        try {
            List<CompletableFuture<StudentCredential>> futures = IntStream
                    .range(0, session.getParsedRows().size())
                    .mapToObj(index -> CompletableFuture.supplyAsync(
                            () -> buildStudentCredential(session, usernames, index),
                            executorService))
                    .toList();
            return futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
        } finally {
            executorService.shutdown();
        }
    }

    private StudentCredential buildStudentCredential(
            ImportSession session,
            List<String> usernames,
            int index) {
        String username = usernames.get(index);
        String plaintextPassword = credentialProvisioning.generateRandomCredential();
        Student student = Student.create(
                username,
                session.getOrganizationId(),
                session.getHostId(),
                username,
                null,
                null,
                null,
                null,
                credentialProvisioning.hashImportedPassword(plaintextPassword));
        CredentialRow credentialRow = new CredentialRow(
                index + 1,
                username,
                plaintextPassword);
        return new StudentCredential(student, credentialRow);
    }

    private boolean startConfirming(ImportSession session) {
        return session.transitionState(ImportSessionState.PARSED, ImportSessionState.CONFIRMING)
                || session.transitionState(ImportSessionState.PREVIEWED, ImportSessionState.CONFIRMING);
    }

    private ImportSession loadSession(
            String hostId,
            Long organizationId,
            String importId) {
        return importSessionCache.getSession(hostId, organizationId, importId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED));
    }

    private record StudentCredential(
            Student student,
            CredentialRow credentialRow) {
    }
}
