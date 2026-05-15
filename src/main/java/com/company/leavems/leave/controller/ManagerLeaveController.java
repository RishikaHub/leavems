package com.company.leavems.leave.controller;

import com.company.leavems.common.web.CurrentUserService;
import com.company.leavems.leave.domain.LeaveStatus;
import com.company.leavems.leave.domain.LeaveType;
import com.company.leavems.leave.dto.DecisionRequest;
import com.company.leavems.leave.service.LeaveRequestService;
import com.company.leavems.user.domain.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/manager")
public class ManagerLeaveController {

    private final LeaveRequestService leaveRequestService;
    private final CurrentUserService currentUserService;

    public ManagerLeaveController(
        LeaveRequestService leaveRequestService,
        CurrentUserService currentUserService
    ) {
        this.leaveRequestService = leaveRequestService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/requests")
    public String managerRequests(
        @RequestParam(required = false) LeaveStatus status,
        @RequestParam(required = false) LeaveType leaveType,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        Model model
    ) {
        User currentUser = currentUserService.getCurrentUser();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("requests", leaveRequestService.getManagerTeamRequests(
            currentUser.getId(),
            status,
            leaveType,
            search,
            pageable
        ));
        model.addAttribute("status", status == null ? "" : status.name());
        model.addAttribute("leaveType", leaveType == null ? "" : leaveType.name());
        model.addAttribute("search", search);

        return "manager/requests";
    }

    @PostMapping("/requests/{id}/approve")
    public String approve(
        @PathVariable UUID id,
        @RequestParam(required = false) String managerNote,
        RedirectAttributes redirectAttributes
    ) {
        User currentUser = currentUserService.getCurrentUser();

        try {
            leaveRequestService.approveRequest(
                currentUser.getId(),
                id,
                new DecisionRequest(managerNote)
            );
            redirectAttributes.addFlashAttribute("success", "Request approved.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/manager/requests";
    }

    @PostMapping("/requests/{id}/reject")
    public String reject(
        @PathVariable UUID id,
        @RequestParam(required = false) String managerNote,
        RedirectAttributes redirectAttributes
    ) {
        User currentUser = currentUserService.getCurrentUser();

        try {
            leaveRequestService.rejectRequest(
                currentUser.getId(),
                id,
                new DecisionRequest(managerNote)
            );
            redirectAttributes.addFlashAttribute("success", "Request rejected.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/manager/requests";
    }
}
