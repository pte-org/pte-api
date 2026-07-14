package com.aptis.modules.iam.service.studentimport;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.modules.iam.domain.OrganizationStudentSequence;
import com.aptis.modules.iam.repository.OrganizationStudentSequenceRepository;
import com.aptis.modules.iam.repository.StudentRepository;

@Service
public class StudentUsernameSequenceService {

    private static final int USERNAME_NUMBER_WIDTH = 6;

    private final OrganizationStudentSequenceRepository sequenceRepository;
    private final StudentRepository studentRepository;

    public StudentUsernameSequenceService(
            OrganizationStudentSequenceRepository sequenceRepository,
            StudentRepository studentRepository) {
        this.sequenceRepository = sequenceRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional
    public List<String> reserveUsernames(Long organizationId, int count) {
        OrganizationStudentSequence sequence = sequenceRepository
                .findByOrganizationIdForUpdate(organizationId)
                .orElseGet(() -> createInitialSequence(organizationId));
        long startValue = sequence.reserve(count);
        List<String> usernames = new ArrayList<>(count);
        for (long value = startValue; value < startValue + count; value++) {
            usernames.add(formatUsername(organizationId, value));
        }
        return usernames;
    }

    private OrganizationStudentSequence createInitialSequence(Long organizationId) {
        long nextValue = studentRepository.countByOrganizationId(organizationId) + 1;
        return sequenceRepository.saveAndFlush(
                OrganizationStudentSequence.create(organizationId, nextValue));
    }

    private String formatUsername(Long organizationId, long value) {
        return "org" + organizationId + "_s" + String.format("%0" + USERNAME_NUMBER_WIDTH + "d", value);
    }
}
