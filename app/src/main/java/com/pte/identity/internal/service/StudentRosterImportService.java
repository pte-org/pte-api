package com.pte.identity.internal.service;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.exception.InvalidRosterFileException;
import com.pte.identity.internal.util.UsernameGenerator;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Imports an arbitrary roster workbook and returns it with generated credentials appended. */
@Service
public class StudentRosterImportService {

    private final RosterFileService rosterFileService;
    private final UserBulkCreateWriter bulkCreateWriter;
    private final TenancyService tenancyService;

    public StudentRosterImportService(RosterFileService rosterFileService,
                                      UserBulkCreateWriter bulkCreateWriter,
                                      TenancyService tenancyService) {
        this.rosterFileService = rosterFileService;
        this.bulkCreateWriter = bulkCreateWriter;
        this.tenancyService = tenancyService;
    }

    /** Checks capacity before writing any student, then creates one account per data row. */
    @Transactional
    public byte[] importRoster(MultipartFile file, CurrentUser caller) {
        byte[] original = rosterFileService.readAndValidate(file);
        int rowCount = rosterFileService.countDataRows(new ByteArrayInputStream(original));
        if (rowCount == 0) {
            throw new InvalidRosterFileException(IdentityConstants.ROSTER_NO_DATA_ROWS);
        }

        UUID tenantId = caller.tenantId();
        tenancyService.assertCanAddStudents(tenantId, rowCount);
        String tenantCode = tenancyService.getTenantCode(tenantId);

        List<RosterFileService.Credential> credentials = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            Optional<UserBulkCreateWriter.Result> result = Optional.empty();
            String username = null;
            for (int attempt = 0; attempt < IdentityConstants.ROSTER_USERNAME_COLLISION_RETRIES; attempt++) {
                username = UsernameGenerator.generate(tenantCode);
                result = bulkCreateWriter.createGeneratedStudent(username, tenantId);
                if (result.isPresent()) {
                    break;
                }
            }
            if (result.isEmpty()) {
                throw new InvalidRosterFileException(IdentityConstants.ROSTER_IMPORT_FAILED);
            }
            credentials.add(new RosterFileService.Credential(username, result.get().generatedPassword()));
        }

        return rosterFileService.appendCredentialColumns(new ByteArrayInputStream(original), credentials);
    }
}
