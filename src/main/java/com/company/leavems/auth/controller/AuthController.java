package com.company.leavems.auth.controller;

import com.company.leavems.auth.dto.AuthResponse;
import com.company.leavems.auth.dto.LoginRequest;
import com.company.leavems.auth.dto.RegisterRequest;
import com.company.leavems.auth.service.AuthService;
import com.company.leavems.user.domain.UserRole;
import com.company.leavems.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @GetMapping("/register")
    public String showRegister(Model model) {
        model.addAttribute("managers", userRepository.findAll()
            .stream()
            .filter(user -> user.getRole() == UserRole.MANAGER)
            .toList());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(
        @RequestParam String fullName,
        @RequestParam String email,
        @RequestParam String password,
        @RequestParam UserRole role,
        @RequestParam(required = false) String managerId,
        Model model
    ) {
        try {
            authService.register(new RegisterRequest(fullName, email, password, role, managerId));
            return "redirect:/auth/login";
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("managers", userRepository.findAll()
                .stream()
                .filter(user -> user.getRole() == UserRole.MANAGER)
                .toList());
            return "auth/register";
        }
    }

    @GetMapping("/login")
    public String showLogin() {
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(
        @RequestParam String email,
        @RequestParam String password,
        HttpServletResponse response,
        Model model
    ) {
        try {
            AuthResponse authResponse = authService.login(new LoginRequest(email, password));

            Cookie cookie = new Cookie("ACCESS_TOKEN", authResponse.token());
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(60 * 60 * 2);
            response.addCookie(cookie);

            return "redirect:/dashboard";
        } catch (Exception ex) {
            model.addAttribute("error", "Invalid email or password.");
            return "auth/login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("ACCESS_TOKEN", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return "redirect:/auth/login";
    }

    @GetMapping("/logout")
    public String logoutGet(HttpServletResponse response) {
        return logout(response);
    }
}
