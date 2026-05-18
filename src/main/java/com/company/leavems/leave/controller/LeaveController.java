package com.company.leavems.leave.controller;

import com.company.leavems.common.web.CurrentUserService;
import com.company.leavems.leave.domain.LeaveStatus;
import com.company.leavems.leave.domain.LeaveType;
import com.company.leavems.leave.dto.CreateLeaveRequest;
import com.company.leavems.leave.dto.LeaveRequestResponse;
import com.company.leavems.leave.dto.UpdateLeaveRequest;
import com.company.leavems.leave.service.LeaveRequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/leave")
public class LeaveController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "startDate",
        "endDate",
        "createdAt",
        "status"
    );

    private final LeaveRequestService leaveRequestService;
    private final CurrentUserService currentUserService;

    public LeaveController(
        LeaveRequestService leaveRequestService,
        CurrentUserService currentUserService
    ) {
        this.leaveRequestService = leaveRequestService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/requests")
    public String listRequests(
        @RequestParam(required = false) LeaveStatus status,
        @RequestParam(required = false) LeaveType leaveType,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "createdAt,desc") String sort,
        Model model
    ) {
        UUID currentUserId = currentUserService.getCurrentUserId();

        Pageable pageable = PageRequest.of(page, size, parseSort(sort));

        Page<LeaveRequestResponse> requestPage = leaveRequestService.getMyRequests(
            currentUserId,
            status,
            leaveType,
            pageable
        );

        model.addAttribute("requests", requestPage);

        model.addAttribute("statusOptions", Arrays.asList(LeaveStatus.values()));
        model.addAttribute("leaveTypeOptions", Arrays.asList(LeaveType.values()));

        model.addAttribute("status", status == null ? "" : status.name());
        model.addAttribute("leaveType", leaveType == null ? "" : leaveType.name());
        model.addAttribute("sort", sort);

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
    public String createRequest(
        @Valid @ModelAttribute("createRequest") CreateLeaveRequest request,
        BindingResult bindingResult,
        RedirectAttributes redirectAttributes,
        Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("leaveTypeOptions", Arrays.asList(LeaveType.values()));
            model.addAttribute("error", "Please correct the highlighted errors.");
            return "leave/create-request";
        }

        try {
            leaveRequestService.createRequest(currentUserService.getCurrentUserId(), request);
            redirectAttributes.addFlashAttribute("success", "Leave request created successfully.");
            return "redirect:/leave/requests";
        } catch (Exception ex) {
            model.addAttribute("leaveTypeOptions", Arrays.asList(LeaveType.values()));
            model.addAttribute("error", ex.getMessage());
            return "leave/create-request";
        }
    }

    @GetMapping("/requests/{id}")
    public String requestDetails(
        @PathVariable UUID id,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        try {
            LeaveRequestResponse response = leaveRequestService.getRequestDetails(
                currentUserService.getCurrentUserId(),
                id
            );
            model.addAttribute("request", response);
            return "leave/request-details";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/leave/requests";
        }
    }

    @GetMapping("/requests/{id}/edit")
    public String editRequestForm(
        @PathVariable UUID id,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        try {
            LeaveRequestResponse response = leaveRequestService.getRequestDetails(
                currentUserService.getCurrentUserId(),
                id
            );

            if (response.status() != LeaveStatus.PENDING) {
                redirectAttributes.addFlashAttribute("error", "Only pending requests can be edited.");
                return "redirect:/leave/requests";
            }

            model.addAttribute(
                "updateRequest",
                new UpdateLeaveRequest(
                    response.leaveType(),
                    response.startDate(),
                    response.endDate(),
                    response.reason()
                )
            );
            model.addAttribute("leaveTypeOptions", Arrays.asList(LeaveType.values()));
            model.addAttribute("requestId", id);

            return "leave/edit-request";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/leave/requests";
        }
    }

    @PostMapping("/requests/{id}/edit")
    public String updateRequest(
        @PathVariable UUID id,
        @Valid @ModelAttribute("updateRequest") UpdateLeaveRequest request,
        BindingResult bindingResult,
        RedirectAttributes redirectAttributes,
        Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("leaveTypeOptions", Arrays.asList(LeaveType.values()));
            model.addAttribute("requestId", id);
            model.addAttribute("error", "Please correct the highlighted errors.");
            return "leave/edit-request";
        }

        try {
            leaveRequestService.updatePendingRequest(
                currentUserService.getCurrentUserId(),
                id,
                request
            );
            redirectAttributes.addFlashAttribute("success", "Leave request updated successfully.");
            return "redirect:/leave/requests";
        } catch (Exception ex) {
            model.addAttribute("leaveTypeOptions", Arrays.asList(LeaveType.values()));
            model.addAttribute("requestId", id);
            model.addAttribute("error", ex.getMessage());
            return "leave/edit-request";
        }
    }

    @PostMapping("/requests/{id}/withdraw")
    public String withdrawRequest(
        @PathVariable UUID id,
        RedirectAttributes redirectAttributes
    ) {
        try {
            leaveRequestService.withdrawRequest(currentUserService.getCurrentUserId(), id);
            redirectAttributes.addFlashAttribute("success", "Leave request withdrawn.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/leave/requests";
    }

    @PostMapping("/requests/{id}/cancel")
    public String cancelRequest(
        @PathVariable UUID id,
        RedirectAttributes redirectAttributes
    ) {
        try {
            leaveRequestService.cancelRequest(currentUserService.getCurrentUserId(), id);
            redirectAttributes.addFlashAttribute("success", "Leave request cancelled and balance restored.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/leave/requests";
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        String[] parts = sort.split(",");
        String field = parts.length > 0 ? parts[0] : "createdAt";
        String direction = parts.length > 1 ? parts[1] : "desc";

        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            field = "createdAt";
        }

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction)
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;

        return Sort.by(sortDirection, field);
    }
}
