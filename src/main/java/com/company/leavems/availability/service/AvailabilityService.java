package com.company.leavems.availability.service;

import com.company.leavems.availability.dto.AvailabilityResponse;
import com.company.leavems.leave.domain.LeaveRequest;
import com.company.leavems.leave.repository.LeaveRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class AvailabilityService {

    private final LeaveRequestRepository leaveRequestRepository;

    public AvailabilityService(LeaveRequestRepository leaveRequestRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
    }

    public Page<AvailabilityResponse> getAvailability(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return leaveRequestRepository.findApprovedAvailability(startDate, endDate, pageable)
                .map(this::toResponse);
    }

    private AvailabilityResponse toResponse(LeaveRequest leaveRequest) {
        UUID requestId = leaveRequest.getId();
        String employeeName = leaveRequest.getRequester().getFullName();
        return new AvailabilityResponse(requestId, employeeName, leaveRequest.getStartDate(), leaveRequest.getEndDate());
    }
}
