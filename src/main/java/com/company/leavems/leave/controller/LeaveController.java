package com.company.leavems.leave.controller;

import com.company.leavems.common.web.CurrentUserService;
import com.company.leavems.leave.domain.LeaveStatus;
import com.company.leavems.leave.domain.LeaveType;
import com.company.leavems.leave.dto.CreateLeaveRequest;
import com.company.leavems.leave.dto.LeaveRequestResponse;
import com.company.leavems.leave.dto.UpdateLeaveRequest;
import com.company.leavems.leave.service.LeaveRequestService;
import com.company.leavems.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.UUID;

@Controller
@RequestMapping("/leave")
public class LeaveController {

    private final LeaveRequestService leaveRequestService;
    private final CurrentUserService currentUserService;

    public LeaveController(LeaveRequestService leaveRequestService, CurrentUserService currentUserService) {
        this.leaveRequestService = leaveRequestService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/requests")
    public String listRequests(@RequestParam(required = false) LeaveStatus status,
                               @RequestParam(required = false) LeaveType leaveType,
                               @PageableDefault(size = 10) Pageable pageable,
                               Model model) {
        UUID currentUserId = currentUserService.getCurrentUserId();
        Page<LeaveRequestResponse> page = leaveRequestService.getMyRequests(currentUserId, status, leaveType, pageable);
        model.addAttribute("requests", page);
        model.addAttribute("statusOptions", Arrays.asList(LeaveStatus.values()));
        model.addAttribute("leaveTypeOptions", Arrays.asList(LeaveType.values()));
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedLeaveType", leaveType);
        return "leave/requests";
    }

    @GetMapping("/requests/new")
    public String newRequestForm(Model model) {
        model.addAttribute("createRequest", new CreateLeaveRequest(null, null, null, null));
        model.addAttribute("leaveTypeOptions", Arrays.asList(LeaveType.values()));
        return "leave/create-request";
    }

    @PostMapping("/requests")
    public String createRequest(@Valid @ModelAttribute("createRequest") CreateLeaveRequest request,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("leaveTypeOptions", Arrays.asList(LeaveType.values()));
            return "leave/create-request";
        }

        try {
            leaveRequestService.createRequest(currentUserService.getCurrentUserId(), request);
            redirectAttributes.addFlashAttribute("successMessage", "Leave request created successfully.");
            return "redirect:/leave/requests";
        } catch (Exception ex) {
            bindingResult.reject("createError", ex.getMessage());
            model.addAttribute("leaveTypeOptions", Arrays.asList(LeaveType.values()));
            return "leave/create-request";
        }
    }

    @GetMapping("/requests/{id}")
    public String requestDetails(@PathVariable UUID id, Model model) {
        LeaveRequestResponse response = leaveRequestService.getRequestDetails(currentUserService.getCurrentUserId(), id);
        model.addAttribute("request", response);
        return "leave/request-detail";
    }

    @GetMapping("/requests/{id}/edit")
    public String editRequestForm(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        LeaveRequestResponse response = leaveRequestService.getRequestDetails(currentUserService.getCurrentUserId(), id);
        if (response.status() != LeaveStatus.PENDING) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only pending requests can be edited.");
            return "redirect:/leave/requests";
        }
        model.addAttribute("updateRequest", new UpdateLeaveRequest(response.leaveType(), response.startDate(), response.endDate(), response.reason()));
        model.addAttribute("leaveTypeOptions", Arrays.asList(LeaveType.values()));
        model.addAttribute("requestId", id);
        return "leave/edit-request";
    }

    @PostMapping("/requests/{id}/edit")
    public String updateRequest(@PathVariable UUID id,
                                @Valid @ModelAttribute("updateRequest") UpdateLeaveRequest request,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("leaveTypeOptions", Arrays.asList(LeaveType.values()));
            model.addAttribute("requestId", id);
            return "leave/edit-request";
        }

        try {
            leaveRequestService.updatePendingRequest(currentUserService.getCurrentUserId(), id, request);
            redirectAttributes.addFlashAttribute("successMessage", "Leave request updated successfully.");
            return "redirect:/leave/requests";
        } catch (Exception ex) {
            bindingResult.reject("updateError", ex.getMessage());
            model.addAttribute("leaveTypeOptions", Arrays.asList(LeaveType.values()));
            model.addAttribute("requestId", id);
            return "leave/edit-request";
        }
    }

    @PostMapping("/requests/{id}/withdraw")
    public String withdrawRequest(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            leaveRequestService.withdrawRequest(currentUserService.getCurrentUserId(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Leave request withdrawn.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/leave/requests";
    }

    @PostMapping("/requests/{id}/cancel")
    public String cancelRequest(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            leaveRequestService.cancelRequest(currentUserService.getCurrentUserId(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Leave request cancelled and balance restored.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/leave/requests";
    }
}
