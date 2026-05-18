package com.company.leavems.auth.service;
import com.company.leavems.auth.dto.AuthResponse;
import com.company.leavems.auth.dto.LoginRequest;
import com.company.leavems.auth.dto.RegisterRequest;
import com.company.leavems.auth.security.JwtUtil;
import com.company.leavems.balance.domain.LeaveBalance;
import com.company.leavems.balance.repository.LeaveBalanceRepository;
import com.company.leavems.common.exception.BusinessException;
import com.company.leavems.common.exception.NotFoundException;
import com.company.leavems.leave.domain.LeaveType;
import com.company.leavems.user.domain.User;
import com.company.leavems.user.domain.UserRole;
import com.company.leavems.user.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthService(
        UserRepository userRepository,
        LeaveBalanceRepository leaveBalanceRepository,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        JwtUtil jwtUtil
    ) {
        this.userRepository = userRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessException("Email already exists.");
        }
      

        UserRole role = request.role() == null ? UserRole.EMPLOYEE : request.role();

        User manager = null;

        if (request.managerId() != null && !request.managerId().isBlank()) {
            UUID managerId = UUID.fromString(request.managerId());

            manager = userRepository.findById(managerId)
                .orElseThrow(() -> new NotFoundException("Manager not found."));

            if (manager.getRole() != UserRole.MANAGER) {
                throw new BusinessException("Selected manager is not a manager.");
            }
        }

        User user = User.builder()
            .fullName(request.fullName())
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .role(role)
            .manager(manager)
            .build();

        User savedUser = userRepository.save(user);

        createDefaultLeaveBalances(savedUser);
    }

    private void createDefaultLeaveBalances(User user) {
        leaveBalanceRepository.save(
            LeaveBalance.builder()
                .user(user)
                .leaveType(LeaveType.VACATION)
                .remainingDays(20)
                .build()
        );

        leaveBalanceRepository.save(
            LeaveBalance.builder()
                .user(user)
                .leaveType(LeaveType.SICK)
                .remainingDays(10)
                .build()
        );

        leaveBalanceRepository.save(
            LeaveBalance.builder()
                .user(user)
                .leaveType(LeaveType.PERSONAL)
                .remainingDays(5)
                .build()
        );
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmailIgnoreCase(request.email())
            .orElseThrow(() -> new NotFoundException("User not found."));

        UserDetails principal = (UserDetails) authentication.getPrincipal();
        String token = jwtUtil.generateToken(principal);

        return new AuthResponse(
            token,
            user.getFullName(),
            user.getEmail(),
            user.getRole().name()
        );
    }
}
