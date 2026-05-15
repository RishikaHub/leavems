package com.company.leavems.auth.dto;

public record AuthResponse(
    String token,
    String fullName,
    String email,
    String role
) {}