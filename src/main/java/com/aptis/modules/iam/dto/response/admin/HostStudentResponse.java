package com.aptis.modules.iam.dto.response.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.aptis.modules.iam.domain.Student;
import com.aptis.modules.iam.domain.enums.UserStatus;

public record HostStudentResponse(
        Long id,
        String username,
        String fullName,
        String studentCode,
        String className,
        String email,
        String phone,
        LocalDate dateOfBirth,
        UserStatus status,
        LocalDateTime createdAt) {

    public static HostStudentResponse from(Student student) {
        return new HostStudentResponse(
                student.getId(),
                student.getUsername(),
                student.getFullName(),
                student.getStudentCode(),
                student.getClassName(),
                student.getEmail(),
                student.getPhone(),
                student.getDateOfBirth(),
                student.getStatus(),
                student.getCreatedAt());
    }
}
