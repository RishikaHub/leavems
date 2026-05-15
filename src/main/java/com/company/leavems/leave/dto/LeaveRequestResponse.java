package com.company.leavems.leave.dto;

import com.company.leavems.leave.domain.LeaveStatus;
import com.company.leavems.leave.domain.LeaveType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record LeaveRequestResponse(
        UUID id,
        UUID requesterId,
        String requesterName,
        LeaveType leaveType,
        LeaveStatus status,
        LocalDate startDate,
        LocalDate endDate,
        int dayCount,
        String reason,
        String managerNote,
        UUID decidedById,
        String decidedByName,
        LocalDateTime decidedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
