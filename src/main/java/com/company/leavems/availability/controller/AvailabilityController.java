package com.company.leavems.availability.controller;

import com.company.leavems.availability.dto.AvailabilityResponse;
import com.company.leavems.availability.service.AvailabilityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequestMapping("/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public String availabilityIndex(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 10) Pageable pageable,
            Model model) {
        LocalDate today = LocalDate.now();
        LocalDate from = startDate != null ? startDate : today;
        LocalDate to = endDate != null ? endDate : today.plusDays(30);

        Page<AvailabilityResponse> page = availabilityService.getAvailability(from, to, pageable);
        model.addAttribute("availabilityPage", page);
        model.addAttribute("startDate", from);
        model.addAttribute("endDate", to);
        return "availability/index";
    }
}
