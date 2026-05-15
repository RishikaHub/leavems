package com.company.leavems.balance.service;

import com.company.leavems.balance.domain.LeaveBalance;
import com.company.leavems.balance.repository.LeaveBalanceRepository;
import com.company.leavems.leave.domain.LeaveType;
import com.company.leavems.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LeaveBalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;

    public LeaveBalanceService(LeaveBalanceRepository leaveBalanceRepository) {
        this.leaveBalanceRepository = leaveBalanceRepository;
    }

    public List<LeaveBalance> getBalancesForUser(UUID userId) {
        return leaveBalanceRepository.findByUserId(userId);
    }

    @Transactional
    public void createDefaultBalances(User user) {
        createBalanceIfMissing(user, LeaveType.VACATION, 20);
        createBalanceIfMissing(user, LeaveType.SICK, 10);
        createBalanceIfMissing(user, LeaveType.PERSONAL, 5);
    }

    private void createBalanceIfMissing(User user, LeaveType leaveType, int days) {
        leaveBalanceRepository.findByUserIdAndLeaveType(user.getId(), leaveType)
                .orElseGet(() -> leaveBalanceRepository.save(LeaveBalance.builder()
                        .user(user)
                        .leaveType(leaveType)
                        .remainingDays(days)
                        .build()));
    }
}
