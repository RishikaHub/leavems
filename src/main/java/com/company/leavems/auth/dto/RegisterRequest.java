package com.company.leavems.auth.dto;

import com.company.leavems.user.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank
    @Size(max = 150)
    String fullName,

    @Email
    @NotBlank
    @Size(max = 255)
    String email,

    @NotBlank
    @Size(min = 6, max = 100)
    String password,

    UserRole role,

    String managerId
) {
}
