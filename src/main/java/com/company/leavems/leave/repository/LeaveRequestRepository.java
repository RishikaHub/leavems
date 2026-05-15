package com.company.leavems.leave.repository;

import com.company.leavems.leave.domain.LeaveRequest;
import com.company.leavems.leave.domain.LeaveStatus;
import com.company.leavems.leave.domain.LeaveType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.id = :id")
    Optional<LeaveRequest> findByIdForUpdate(UUID id);

    @Query("SELECT COUNT(lr) > 0 FROM LeaveRequest lr WHERE lr.requester.id = :requesterId " +
           "AND lr.status IN ('PENDING', 'APPROVED') " +
           "AND lr.startDate <= :endDate AND lr.endDate >= :startDate " +
           "AND (:excludeId IS NULL OR lr.id != :excludeId)")
    boolean existsOverlappingActiveRequest(@Param("requesterId") UUID requesterId,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate,
                                           @Param("excludeId") UUID excludeId);

    @Query("SELECT CASE WHEN COUNT(lr) > 0 THEN true ELSE false END FROM LeaveRequest lr " +
           "WHERE lr.id = :requestId AND lr.requester.manager.id = :managerId")
    boolean isDirectReportRequest(@Param("requestId") UUID requestId, @Param("managerId") UUID managerId);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.requester.manager.id = :managerId AND lr.status = 'PENDING'")
    Page<LeaveRequest> findPendingApprovals(@Param("managerId") UUID managerId, Pageable pageable);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.requester.id = :requesterId " +
           "AND (:status IS NULL OR lr.status = :status) " +
           "AND (:leaveType IS NULL OR lr.leaveType = :leaveType)")
    Page<LeaveRequest> findMyRequests(@Param("requesterId") UUID requesterId,
                                      @Param("status") LeaveStatus status,
                                      @Param("leaveType") LeaveType leaveType,
                                      Pageable pageable);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.requester.manager.id = :managerId " +
           "AND (:status IS NULL OR lr.status = :status) " +
           "AND (:leaveType IS NULL OR lr.leaveType = :leaveType) " +
           "AND (:search IS NULL OR LOWER(lr.requester.fullName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<LeaveRequest> findManagerTeamRequests(@Param("managerId") UUID managerId,
                                               @Param("status") LeaveStatus status,
                                               @Param("leaveType") LeaveType leaveType,
                                               @Param("search") String search,
                                               Pageable pageable);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = 'APPROVED' " +
           "AND lr.startDate <= :endDate AND lr.endDate >= :startDate")
    Page<LeaveRequest> findApprovedAvailability(@Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate,
                                                Pageable pageable);
}