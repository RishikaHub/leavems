package com.company.leavems.leave.service;

import com.company.leavems.balance.domain.LeaveBalance;
import com.company.leavems.balance.repository.LeaveBalanceRepository;
import com.company.leavems.common.exception.BusinessException;
import com.company.leavems.common.exception.ForbiddenException;
import com.company.leavems.leave.domain.LeaveRequest;
import com.company.leavems.leave.domain.LeaveStatus;
import com.company.leavems.leave.domain.LeaveType;
import com.company.leavems.leave.dto.CreateLeaveRequest;
import com.company.leavems.leave.dto.DecisionRequest;
import com.company.leavems.leave.repository.LeaveRequestRepository;
import com.company.leavems.user.domain.User;
import com.company.leavems.user.domain.UserRole;
import com.company.leavems.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    private LeaveRequestService leaveRequestService;

    private UUID managerId;
    private UUID otherManagerId;
    private UUID employeeId;
    private User manager;
    private User otherManager;
    private User employee;

    @BeforeEach
    void setUp() {
        leaveRequestService = new LeaveRequestService(
            leaveRequestRepository,
            userRepository,
            leaveBalanceRepository
        );

        managerId = UUID.randomUUID();
        otherManagerId = UUID.randomUUID();
        employeeId = UUID.randomUUID();

        manager = User.builder()
            .id(managerId)
            .fullName("Demo Manager")
            .email("manager@example.com")
            .role(UserRole.MANAGER)
            .passwordHash("hash")
            .build();

        otherManager = User.builder()
            .id(otherManagerId)
            .fullName("Other Manager")
            .email("other.manager@example.com")
            .role(UserRole.MANAGER)
            .passwordHash("hash")
            .build();

        employee = User.builder()
            .id(employeeId)
            .fullName("Demo Employee")
            .email("employee@example.com")
            .role(UserRole.EMPLOYEE)
            .passwordHash("hash")
            .manager(manager)
            .build();
    }

    @Test
    void createRequestCreatesPendingRequestWithoutDeductingBalance() {
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = start.plusDays(2);
        LeaveBalance balance = balance(LeaveType.VACATION, 20);

        when(userRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(leaveRequestRepository.existsOverlappingActiveRequest(employeeId, start, end, null))
            .thenReturn(false);
        when(leaveBalanceRepository.findByUserIdAndLeaveTypeForUpdate(employeeId, LeaveType.VACATION))
            .thenReturn(Optional.of(balance));
        when(leaveRequestRepository.save(any(LeaveRequest.class)))
            .thenAnswer(invocation -> {
                LeaveRequest request = invocation.getArgument(0);
                request.setId(UUID.randomUUID());
                return request;
            });

        var response = leaveRequestService.createRequest(
            employeeId,
            new CreateLeaveRequest(LeaveType.VACATION, start, end, "Family trip")
        );

        assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);
        assertThat(response.requesterId()).isEqualTo(employeeId);
        assertThat(response.dayCount()).isEqualTo(3);
        assertThat(balance.getRemainingDays()).isEqualTo(20);

        ArgumentCaptor<LeaveRequest> captor = ArgumentCaptor.forClass(LeaveRequest.class);
        verify(leaveRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(captor.getValue().getRequester()).isEqualTo(employee);
    }

    @Test
    void createRequestRejectsEndDateBeforeStartDate() {
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = start.minusDays(1);

        assertThatThrownBy(() -> leaveRequestService.createRequest(
            employeeId,
            new CreateLeaveRequest(LeaveType.VACATION, start, end, "Invalid range")
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("End date");

        verifyNoInteractions(userRepository, leaveRequestRepository, leaveBalanceRepository);
    }

    @Test
    void createRequestRejectsOverlappingPendingOrApprovedRequest() {
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = start.plusDays(1);

        when(userRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(leaveRequestRepository.existsOverlappingActiveRequest(employeeId, start, end, null))
            .thenReturn(true);

        assertThatThrownBy(() -> leaveRequestService.createRequest(
            employeeId,
            new CreateLeaveRequest(LeaveType.SICK, start, end, "Overlap")
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("overlaps");

        verify(leaveRequestRepository, never()).save(any());
        verifyNoInteractions(leaveBalanceRepository);
    }

    @Test
    void createRequestRejectsInsufficientBalance() {
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = start.plusDays(5);
        LeaveBalance balance = balance(LeaveType.PERSONAL, 2);

        when(userRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(leaveRequestRepository.existsOverlappingActiveRequest(employeeId, start, end, null))
            .thenReturn(false);
        when(leaveBalanceRepository.findByUserIdAndLeaveTypeForUpdate(employeeId, LeaveType.PERSONAL))
            .thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> leaveRequestService.createRequest(
            employeeId,
            new CreateLeaveRequest(LeaveType.PERSONAL, start, end, "Too long")
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Insufficient");

        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void approveRequestAllowsOnlyDirectManagerAndDeductsBalance() {
        UUID requestId = UUID.randomUUID();
        LeaveRequest request = pendingRequest(requestId, LeaveType.VACATION, 4);
        LeaveBalance balance = balance(LeaveType.VACATION, 20);

        when(leaveRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(leaveBalanceRepository.findByUserIdAndLeaveTypeForUpdate(employeeId, LeaveType.VACATION))
            .thenReturn(Optional.of(balance));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = leaveRequestService.approveRequest(
            managerId,
            requestId,
            new DecisionRequest("Approved")
        );

        assertThat(response.status()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(response.decidedById()).isEqualTo(managerId);
        assertThat(response.decidedAt()).isNotNull();
        assertThat(response.managerNote()).isEqualTo("Approved");
        assertThat(balance.getRemainingDays()).isEqualTo(16);
    }

    @Test
    void approveRequestRejectsNonDirectManager() {
        UUID requestId = UUID.randomUUID();
        LeaveRequest request = pendingRequest(requestId, LeaveType.VACATION, 2);

        when(leaveRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(userRepository.findById(otherManagerId)).thenReturn(Optional.of(otherManager));

        assertThatThrownBy(() -> leaveRequestService.approveRequest(
            otherManagerId,
            requestId,
            new DecisionRequest("Nope")
        ))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("direct manager");

        assertThat(request.getStatus()).isEqualTo(LeaveStatus.PENDING);
        verifyNoInteractions(leaveBalanceRepository);
    }

    @Test
    void approveRequestRejectsWhenBalanceChangedAtApprovalTime() {
        UUID requestId = UUID.randomUUID();
        LeaveRequest request = pendingRequest(requestId, LeaveType.VACATION, 5);
        LeaveBalance balance = balance(LeaveType.VACATION, 3);

        when(leaveRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(leaveBalanceRepository.findByUserIdAndLeaveTypeForUpdate(employeeId, LeaveType.VACATION))
            .thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> leaveRequestService.approveRequest(
            managerId,
            requestId,
            new DecisionRequest("Approved")
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("approval time");

        assertThat(request.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(balance.getRemainingDays()).isEqualTo(3);
        verify(leaveRequestRepository, never()).save(any());
    }

    @Test
    void rejectRequestRecordsDecisionWithoutChangingBalance() {
        UUID requestId = UUID.randomUUID();
        LeaveRequest request = pendingRequest(requestId, LeaveType.SICK, 1);

        when(leaveRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = leaveRequestService.rejectRequest(
            managerId,
            requestId,
            new DecisionRequest("Release week")
        );

        assertThat(response.status()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(response.managerNote()).isEqualTo("Release week");
        assertThat(response.decidedById()).isEqualTo(managerId);
        verifyNoInteractions(leaveBalanceRepository);
    }

    @Test
    void withdrawPendingRequestCancelsWithoutBalanceChange() {
        UUID requestId = UUID.randomUUID();
        LeaveRequest request = pendingRequest(requestId, LeaveType.SICK, 1);

        when(leaveRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        leaveRequestService.withdrawRequest(employeeId, requestId);

        assertThat(request.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
        verify(leaveRequestRepository).save(request);
        verifyNoInteractions(leaveBalanceRepository);
    }

    @Test
    void cancelApprovedFutureRequestRestoresBalance() {
        UUID requestId = UUID.randomUUID();
        LeaveRequest request = approvedFutureRequest(requestId, LeaveType.VACATION, 2);
        LeaveBalance balance = balance(LeaveType.VACATION, 18);

        when(leaveRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(leaveBalanceRepository.findByUserIdAndLeaveTypeForUpdate(employeeId, LeaveType.VACATION))
            .thenReturn(Optional.of(balance));

        leaveRequestService.cancelRequest(employeeId, requestId);

        assertThat(request.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
        assertThat(balance.getRemainingDays()).isEqualTo(20);
        verify(leaveBalanceRepository).save(balance);
        verify(leaveRequestRepository).save(request);
    }

    @Test
    void requestDetailsAllowsRequesterAndDirectManagerOnly() {
        UUID requestId = UUID.randomUUID();
        LeaveRequest request = pendingRequest(requestId, LeaveType.PERSONAL, 1);

        when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThat(leaveRequestService.getRequestDetails(employeeId, requestId).id()).isEqualTo(requestId);
        assertThat(leaveRequestService.getRequestDetails(managerId, requestId).id()).isEqualTo(requestId);

        assertThatThrownBy(() -> leaveRequestService.getRequestDetails(otherManagerId, requestId))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void managerTeamRequestsRequiresManagerRole() {
        when(userRepository.findById(employeeId)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> leaveRequestService.getManagerTeamRequests(
            employeeId,
            null,
            null,
            null,
            PageRequest.of(0, 20)
        ))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("Only managers");

        verify(leaveRequestRepository, never()).findManagerTeamRequests(any(), any(), any(), any(), any());
    }

    @Test
    void managerTeamRequestsDelegatesFiltersSearchAndPagination() {
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(leaveRequestRepository.findManagerTeamRequests(
            eq(managerId),
            eq(LeaveStatus.PENDING),
            eq(LeaveType.VACATION),
            eq("Demo"),
            any()
        )).thenReturn(new PageImpl<>(List.of(pendingRequest(UUID.randomUUID(), LeaveType.VACATION, 1))));

        var page = leaveRequestService.getManagerTeamRequests(
            managerId,
            LeaveStatus.PENDING,
            LeaveType.VACATION,
            " Demo ",
            PageRequest.of(0, 20)
        );

        assertThat(page.getContent()).hasSize(1);
    }

    private LeaveBalance balance(LeaveType leaveType, int remainingDays) {
        return LeaveBalance.builder()
            .id(UUID.randomUUID())
            .user(employee)
            .leaveType(leaveType)
            .remainingDays(remainingDays)
            .build();
    }

    private LeaveRequest pendingRequest(UUID requestId, LeaveType leaveType, int days) {
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = start.plusDays(days - 1L);

        return LeaveRequest.builder()
            .id(requestId)
            .requester(employee)
            .leaveType(leaveType)
            .startDate(start)
            .endDate(end)
            .dayCount(days)
            .reason("Test")
            .status(LeaveStatus.PENDING)
            .build();
    }

    private LeaveRequest approvedFutureRequest(UUID requestId, LeaveType leaveType, int days) {
        LeaveRequest request = pendingRequest(requestId, leaveType, days);
        request.setStatus(LeaveStatus.APPROVED);
        request.setDecidedBy(manager);
        request.setDecidedAt(java.time.LocalDateTime.now());
        return request;
    }
}
