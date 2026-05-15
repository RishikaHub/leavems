package com.company.leavems.availability.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AvailabilityResponse(
        UUID requestId,
        String employeeName,
        LocalDate startDate,
        LocalDate endDate
) {
}
