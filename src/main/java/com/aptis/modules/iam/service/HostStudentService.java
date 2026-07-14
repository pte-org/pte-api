package com.aptis.modules.iam.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.modules.iam.dto.response.admin.HostStudentResponse;
import com.aptis.modules.iam.repository.StudentRepository;

@Service
public class HostStudentService {

    private final StudentRepository studentRepository;

    public HostStudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public Page<HostStudentResponse> listStudents(Long organizationId, Pageable pageable) {
        return studentRepository
                .findByOrganizationIdAndDeletedFalse(organizationId, pageable)
                .map(HostStudentResponse::from);
    }
}
