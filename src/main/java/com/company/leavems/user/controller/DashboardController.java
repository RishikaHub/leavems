package com.company.leavems.user.controller;

import com.company.leavems.balance.domain.LeaveBalance;
import com.company.leavems.balance.service.LeaveBalanceService;
import com.company.leavems.common.web.CurrentUserService;
import com.company.leavems.leave.repository.LeaveRequestRepository;
import com.company.leavems.user.domain.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

@Controller
public class DashboardController {

    private final CurrentUserService currentUserService;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveRequestRepository leaveRequestRepository;

    public DashboardController(CurrentUserService currentUserService,
                               LeaveBalanceService leaveBalanceService,
                               LeaveRequestRepository leaveRequestRepository) {
        this.currentUserService = currentUserService;
        this.leaveBalanceService = leaveBalanceService;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @GetMapping("/")
    public String rootRedirect() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        User currentUser = currentUserService.getCurrentUser();
        UUID currentUserId = currentUserService.getCurrentUserId();
        List<LeaveBalance> balances = leaveBalanceService.getBalancesForUser(currentUserId);

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("balances", balances);
        model.addAttribute("role", currentUser.getRole());

        if (currentUser.isManager()) {
            long pendingCount = leaveRequestRepository.findPendingApprovals(currentUserId, org.springframework.data.domain.Pageable.unpaged()).getTotalElements();
            model.addAttribute("pendingApprovalsCount", pendingCount);
        }

        return "dashboard";
    }
}
