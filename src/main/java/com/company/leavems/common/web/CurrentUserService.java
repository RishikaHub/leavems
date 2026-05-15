package com.company.leavems.common.web;

import com.company.leavems.common.exception.ForbiddenException;
import com.company.leavems.common.exception.NotFoundException;
import com.company.leavems.user.domain.User;
import com.company.leavems.user.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        String email = getAuthenticatedEmail();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("Current user not found"));
    }

    public UUID getCurrentUserId() {
        return getCurrentUser().getId();
    }

    private String getAuthenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw new ForbiddenException("No authenticated user");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof org.springframework.security.core.userdetails.User userDetails) {
            return userDetails.getUsername();
        }

        if (principal instanceof String username) {
            return username;
        }

        throw new ForbiddenException("No authenticated user");
    }
}
