package com.aptis.modules.examoperations.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "credential_export")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class CredentialExport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String hostId;

    @Column(nullable = false)
    private Long importBatchId;

    @Column(nullable = false)
    private String exportedBy;

    @Column(nullable = false)
    private String type; // STUDENT_CREDENTIALS, TEACHER_CREDENTIALS

    @Column(nullable = false)
    private String fileUrl;

    private LocalDateTime createdAt;

    // NO business methods — anemic entity (FR-05)
}
