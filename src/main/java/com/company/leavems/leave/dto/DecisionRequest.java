package com.company.leavems.leave.dto;

import jakarta.validation.constraints.Size;

public record DecisionRequest(
        @Size(max = 500) String managerNote
) {
}
