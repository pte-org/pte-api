package com.aptis.modules.iam.dto.request.admin;

public record CreateAdminRequest(String email, String name, String rawPassword) {
}
