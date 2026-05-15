package com.company.leavems.balance.repository;

import com.company.leavems.balance.domain.LeaveBalance;
import com.company.leavems.leave.domain.LeaveType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {

    Optional<LeaveBalance> findByUserIdAndLeaveType(UUID userId, LeaveType leaveType);

    List<LeaveBalance> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lb FROM LeaveBalance lb WHERE lb.user.id = :userId AND lb.leaveType = :leaveType")
    Optional<LeaveBalance> findByUserIdAndLeaveTypeForUpdate(UUID userId, LeaveType leaveType);
}