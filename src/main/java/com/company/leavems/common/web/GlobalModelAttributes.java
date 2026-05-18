package com.company.leavems.common.web;

import com.company.leavems.balance.repository.LeaveBalanceRepository;
import com.company.leavems.user.domain.User;
import com.company.leavems.user.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
public class GlobalModelAttributes {

    private final UserRepository userRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

    public GlobalModelAttributes(
        UserRepository userRepository,
        LeaveBalanceRepository leaveBalanceRepository
    ) {
        this.userRepository = userRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
    }

    @ModelAttribute("currentUser")
    public User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
            || !authentication.isAuthenticated()
            || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        String email = authentication.getName();

        return userRepository.findByEmailIgnoreCase(email).orElse(null);
    }

    @ModelAttribute("balances")
    public Object balances() {
        User user = currentUser();

        if (user == null || user.getId() == null) {
            return List.of();
        }

        return leaveBalanceRepository.findByUserId(user.getId());
    }
}
