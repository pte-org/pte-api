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

/**
 * Stores plaintext credentials temporarily after student provisioning.
 * Consumed by FS2-08 (credential export) then deleted in the same transaction.
 */
@Entity
@Table(name = "student_credential_pending")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "plaintextCredential")
public class StudentCredentialPending {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true)
    private Long studentId;

    @Column(nullable = false)
    private Long importBatchId;

    @Column(nullable = false)
    private String plaintextCredential;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * Factory method — creates a pending credential record.
     */
    public static StudentCredentialPending create(
            Long studentId,
            Long importBatchId,
            String plaintextCredential) {
        StudentCredentialPending pending = new StudentCredentialPending();
        pending.setStudentId(studentId);
        pending.setImportBatchId(importBatchId);
        pending.setPlaintextCredential(plaintextCredential);
        pending.setCreatedAt(LocalDateTime.now());
        return pending;
    }
}
