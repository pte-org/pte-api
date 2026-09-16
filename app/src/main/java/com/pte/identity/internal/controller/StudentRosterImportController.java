package com.pte.identity.internal.controller;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.service.StudentRosterImportService;
import com.pte.shared.security.CurrentUserContext;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Host-only endpoint for in-memory roster import and credential export. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class StudentRosterImportController {

    private final StudentRosterImportService rosterImportService;

    public StudentRosterImportController(StudentRosterImportService rosterImportService) {
        this.rosterImportService = rosterImportService;
    }

    @PostMapping(value = "/students/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = IdentityConstants.ROSTER_CONTENT_TYPE)
    public ResponseEntity<byte[]> importRoster(
            @RequestPart(value = "file", required = false) MultipartFile file) {
        byte[] exported = rosterImportService.importRoster(file, CurrentUserContext.required());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(IdentityConstants.ROSTER_OUTPUT_FILENAME)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(IdentityConstants.ROSTER_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(exported);
    }
}
