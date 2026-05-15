package com.company.leavems.leave.service;

import com.company.leavems.balance.domain.LeaveBalance;
import com.company.leavems.balance.repository.LeaveBalanceRepository;
import com.company.leavems.common.exception.BusinessException;
import com.company.leavems.common.exception.ForbiddenException;
import com.company.leavems.common.exception.NotFoundException;
import com.company.leavems.leave.domain.LeaveRequest;
import com.company.leavems.leave.domain.LeaveStatus;
import com.company.leavems.leave.domain.LeaveType;
import com.company.leavems.leave.dto.CreateLeaveRequest;
import com.company.leavems.leave.dto.DecisionRequest;
import com.company.leavems.leave.dto.LeaveRequestResponse;
import com.company.leavems.leave.dto.UpdateLeaveRequest;
import com.company.leavems.leave.repository.LeaveRequestRepository;
import com.company.leavems.user.domain.User;
import com.company.leavems.user.domain.UserRole;
import com.company.leavems.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

    public LeaveRequestService(
        LeaveRequestRepository leaveRequestRepository,
        UserRepository userRepository,
        LeaveBalanceRepository leaveBalanceRepository
    ) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
    }

    @Transactional
    public LeaveRequestResponse createRequest(UUID currentUserId, CreateLeaveRequest request) {
        validateCreateOrUpdateRequest(request.leaveType(), request.startDate(), request.endDate());

        User requester = findUser(currentUserId);

        boolean overlaps = leaveRequestRepository.existsOverlappingActiveRequest(
            currentUserId,
            request.startDate(),
            request.endDate(),
            null
        );

        if (overlaps) {
            throw new BusinessException("A pending or approved leave request already overlaps the selected dates.");
        }

        int requestedDays = calculateInclusiveDays(request.startDate(), request.endDate());

        LeaveBalance balance = findLeaveBalanceForUpdate(currentUserId, request.leaveType());

        if (balance.getRemainingDays() < requestedDays) {
            throw new BusinessException(
                "Insufficient leave balance. Available: "
                    + balance.getRemainingDays()
                    + " day(s), requested: "
                    + requestedDays
                    + " day(s)."
            );
        }

        LeaveRequest leaveRequest = LeaveRequest.builder()
            .requester(requester)
            .leaveType(request.leaveType())
            .startDate(request.startDate())
            .endDate(request.endDate())
            .reason(request.reason())
            .status(LeaveStatus.PENDING)
            .dayCount(requestedDays)
            .build();

        return toResponse(leaveRequestRepository.save(leaveRequest));
    }

    @Transactional
    public LeaveRequestResponse updatePendingRequest(UUID currentUserId, UUID requestId, UpdateLeaveRequest request) {
        validateCreateOrUpdateRequest(request.leaveType(), request.startDate(), request.endDate());

        LeaveRequest leaveRequest = findRequestForUpdate(requestId);

        ensureRequester(leaveRequest, currentUserId);

        if (!leaveRequest.isPending()) {
            throw new BusinessException("Only pending requests can be updated.");
        }

        boolean overlaps = leaveRequestRepository.existsOverlappingActiveRequest(
            currentUserId,
            request.startDate(),
            request.endDate(),
            requestId
        );

        if (overlaps) {
            throw new BusinessException("The updated date range overlaps another pending or approved request.");
        }

        int requestedDays = calculateInclusiveDays(request.startDate(), request.endDate());

        LeaveBalance balance = findLeaveBalanceForUpdate(currentUserId, request.leaveType());

        if (balance.getRemainingDays() < requestedDays) {
            throw new BusinessException(
                "Insufficient leave balance. Available: "
                    + balance.getRemainingDays()
                    + " day(s), requested: "
                    + requestedDays
                    + " day(s)."
            );
        }

        leaveRequest.setLeaveType(request.leaveType());
        leaveRequest.setStartDate(request.startDate());
        leaveRequest.setEndDate(request.endDate());
        leaveRequest.setReason(request.reason());
        leaveRequest.setDayCount(requestedDays);

        return toResponse(leaveRequestRepository.save(leaveRequest));
    }

    @Transactional(readOnly = true)
    public LeaveRequestResponse getRequestDetails(UUID currentUserId, UUID requestId) {
        LeaveRequest leaveRequest = findRequest(requestId);

        if (!isRequester(leaveRequest, currentUserId) && !isDirectManager(leaveRequest, currentUserId)) {
            throw new ForbiddenException("You do not have permission to view this leave request.");
        }

        return toResponse(leaveRequest);
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getMyRequests(
        UUID currentUserId,
        LeaveStatus status,
        LeaveType leaveType,
        Pageable pageable
    ) {
        return leaveRequestRepository.findMyRequests(currentUserId, status, leaveType, pageable)
            .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getManagerTeamRequests(
        UUID managerId,
        LeaveStatus status,
        LeaveType leaveType,
        String search,
        Pageable pageable
    ) {
        User manager = findUser(managerId);

        if (manager.getRole() != UserRole.MANAGER) {
            throw new ForbiddenException("Only managers can view team requests.");
        }

        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();

        return leaveRequestRepository.findManagerTeamRequests(
                managerId,
                status,
                leaveType,
                normalizedSearch,
                pageable
            )
            .map(this::toResponse);
    }

    @Transactional
    public void withdrawRequest(UUID currentUserId, UUID requestId) {
        LeaveRequest leaveRequest = findRequestForUpdate(requestId);

        ensureRequester(leaveRequest, currentUserId);

        if (!leaveRequest.isPending()) {
            throw new BusinessException("Only pending requests can be withdrawn.");
        }

        leaveRequest.setStatus(LeaveStatus.CANCELLED);
        leaveRequestRepository.save(leaveRequest);
    }

    @Transactional
    public void cancelRequest(UUID currentUserId, UUID requestId) {
        LeaveRequest leaveRequest = findRequestForUpdate(requestId);

        ensureRequester(leaveRequest, currentUserId);

        if (!leaveRequest.isApproved()) {
            throw new BusinessException("Only approved requests can be cancelled.");
        }

        if (!leaveRequest.getStartDate().isAfter(LocalDate.now())) {
            throw new BusinessException("Only future approved requests can be cancelled.");
        }

        LeaveBalance balance = findLeaveBalanceForUpdate(
            leaveRequest.getRequester().getId(),
            leaveRequest.getLeaveType()
        );

        balance.restore(leaveRequest.getDayCount());
        leaveBalanceRepository.save(balance);

        leaveRequest.setStatus(LeaveStatus.CANCELLED);
        leaveRequestRepository.save(leaveRequest);
    }

    @Transactional
    public LeaveRequestResponse approveRequest(UUID managerId, UUID requestId, DecisionRequest request) {
        LeaveRequest leaveRequest = findRequestForUpdate(requestId);

        ensureManagerCanAct(leaveRequest, managerId);

        if (!leaveRequest.isPending()) {
            throw new BusinessException("Only pending requests can be approved.");
        }

        LeaveBalance balance = findLeaveBalanceForUpdate(
            leaveRequest.getRequester().getId(),
            leaveRequest.getLeaveType()
        );

        int requiredDays = leaveRequest.getDayCount();

        if (balance.getRemainingDays() < requiredDays) {
            throw new BusinessException(
                "Insufficient leave balance at approval time. Available: "
                    + balance.getRemainingDays()
                    + " day(s), requested: "
                    + requiredDays
                    + " day(s)."
            );
        }

        balance.debit(requiredDays);
        leaveBalanceRepository.save(balance);

        User manager = findUser(managerId);

        leaveRequest.setStatus(LeaveStatus.APPROVED);
        leaveRequest.setDecidedBy(manager);
        leaveRequest.setDecidedAt(LocalDateTime.now());
        leaveRequest.setManagerNote(request.managerNote());

        return toResponse(leaveRequestRepository.save(leaveRequest));
    }

    @Transactional
    public LeaveRequestResponse rejectRequest(UUID managerId, UUID requestId, DecisionRequest request) {
        LeaveRequest leaveRequest = findRequestForUpdate(requestId);

        ensureManagerCanAct(leaveRequest, managerId);

        if (!leaveRequest.isPending()) {
            throw new BusinessException("Only pending requests can be rejected.");
        }

        User manager = findUser(managerId);

        leaveRequest.setStatus(LeaveStatus.REJECTED);
        leaveRequest.setDecidedBy(manager);
        leaveRequest.setDecidedAt(LocalDateTime.now());
        leaveRequest.setManagerNote(request.managerNote());

        return toResponse(leaveRequestRepository.save(leaveRequest));
    }

    private void validateCreateOrUpdateRequest(LeaveType leaveType, LocalDate startDate, LocalDate endDate) {
        if (leaveType == null) {
            throw new BusinessException("Leave type is required.");
        }

        if (startDate == null || endDate == null) {
            throw new BusinessException("Start date and end date are required.");
        }

        if (endDate.isBefore(startDate)) {
            throw new BusinessException("End date must be the same as or after start date.");
        }
    }

    private int calculateInclusiveDays(LocalDate startDate, LocalDate endDate) {
        return Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found."));
    }

    private LeaveRequest findRequest(UUID requestId) {
        return leaveRequestRepository.findById(requestId)
            .orElseThrow(() -> new NotFoundException("Leave request not found."));
    }

    private LeaveRequest findRequestForUpdate(UUID requestId) {
        return leaveRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new NotFoundException("Leave request not found."));
    }

    private LeaveBalance findLeaveBalanceForUpdate(UUID userId, LeaveType leaveType) {
        return leaveBalanceRepository.findByUserIdAndLeaveTypeForUpdate(userId, leaveType)
            .orElseThrow(() -> new BusinessException("Leave balance not found for selected type."));
    }

    private void ensureRequester(LeaveRequest leaveRequest, UUID currentUserId) {
        if (!isRequester(leaveRequest, currentUserId)) {
            throw new ForbiddenException("Only the requester can perform this action.");
        }
    }

    private boolean isRequester(LeaveRequest leaveRequest, UUID currentUserId) {
        return leaveRequest.getRequester() != null
            && leaveRequest.getRequester().getId() != null
            && leaveRequest.getRequester().getId().equals(currentUserId);
    }

    private boolean isDirectManager(LeaveRequest leaveRequest, UUID currentUserId) {
        return leaveRequest.getRequester() != null
            && leaveRequest.getRequester().getManager() != null
            && leaveRequest.getRequester().getManager().getId() != null
            && leaveRequest.getRequester().getManager().getId().equals(currentUserId);
    }

    private void ensureManagerCanAct(LeaveRequest leaveRequest, UUID managerId) {
        User manager = findUser(managerId);

        if (manager.getRole() != UserRole.MANAGER) {
            throw new ForbiddenException("Only a manager may approve or reject requests.");
        }

        if (!isDirectManager(leaveRequest, managerId)) {
            throw new ForbiddenException("Only the requester's direct manager can approve or reject this request.");
        }
    }

    private LeaveRequestResponse toResponse(LeaveRequest leaveRequest) {
        UUID decidedById = leaveRequest.getDecidedBy() != null
            ? leaveRequest.getDecidedBy().getId()
            : null;

        String decidedByName = leaveRequest.getDecidedBy() != null
            ? leaveRequest.getDecidedBy().getFullName()
            : null;

        return new LeaveRequestResponse(
            leaveRequest.getId(),
            leaveRequest.getRequester().getId(),
            leaveRequest.getRequester().getFullName(),
            leaveRequest.getLeaveType(),
            leaveRequest.getStatus(),
            leaveRequest.getStartDate(),
            leaveRequest.getEndDate(),
            leaveRequest.getDayCount(),
            leaveRequest.getReason(),
            leaveRequest.getManagerNote(),
            decidedById,
            decidedByName,
            leaveRequest.getDecidedAt(),
            leaveRequest.getCreatedAt(),
            leaveRequest.getUpdatedAt()
        );
    }
}
