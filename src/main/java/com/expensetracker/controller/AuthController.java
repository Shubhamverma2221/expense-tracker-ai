package com.expensetracker.controller;

import com.expensetracker.config.FlashHelper;
import com.expensetracker.model.User;
import com.expensetracker.repository.ExpenseRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.YearMonth;

@Controller
public class AuthController {

    private final ExpenseRepository repository;

    public AuthController(ExpenseRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model) {
        if (session.getAttribute("user_id") != null) {
            return "redirect:/dashboard";
        }
        model.addAttribute("title", "Sign In - AI Expense Tracker");
        return "auth/login";
    }

    @PostMapping("/login")
    public String handleLogin(@RequestParam(name = "email", defaultValue = "") String email,
                              @RequestParam(name = "password", defaultValue = "") String password,
                              @RequestParam(name = "next", required = false) String nextUrl,
                              HttpServletRequest request,
                              HttpSession session,
                              Model model) {
        if (email.isBlank() || password.isBlank()) {
            FlashHelper.flash(session, "Please enter both your email address and password.", "warning");
            model.addAttribute("email", email);
            return "auth/login";
        }

        User user = repository.authenticateUser(email, password);
        if (user != null) {
            session.setAttribute("user_id", user.getId());
            session.setAttribute("user_name", user.getName());
            session.setAttribute("user_email", user.getEmail());
            session.setAttribute("user_role", user.getRole());
            session.setAttribute("user_currency", user.getCurrency());

            String ip = request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
            repository.logAudit("User Login", user.getId(), user.getEmail(), ip);

            String firstName = user.getName().split(" ")[0];
            FlashHelper.flash(session, "Welcome back, " + firstName + "!", "success");

            if (nextUrl != null && nextUrl.startsWith("/") && !nextUrl.startsWith("//")) {
                return "redirect:" + nextUrl;
            }
            return "redirect:/dashboard";
        } else {
            FlashHelper.flash(session, "Invalid email address or password. Please verify your credentials.", "danger");
            model.addAttribute("email", email);
            return "auth/login";
        }
    }

    @GetMapping("/guest-login")
    public String guestLogin(HttpServletRequest request, HttpSession session) {
        User demoUser = repository.getUserByEmail("guest@expensetracker.ai");
        if (demoUser == null) {
            repository.seedDefaultUsersAndData();
            demoUser = repository.getUserByEmail("guest@expensetracker.ai");
        }

        if (demoUser != null) {
            session.setAttribute("user_id", demoUser.getId());
            session.setAttribute("user_name", demoUser.getName());
            session.setAttribute("user_email", demoUser.getEmail());
            session.setAttribute("user_role", demoUser.getRole());
            session.setAttribute("user_currency", demoUser.getCurrency());

            String ip = request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
            repository.logAudit("Demo Login", demoUser.getId(), demoUser.getEmail(), ip);

            FlashHelper.flash(session, "Logged into Demo Account! Feel free to explore all features.", "success");
            return "redirect:/dashboard";
        }

        FlashHelper.flash(session, "Could not initialize guest demo session.", "danger");
        return "redirect:/login";
    }

    @GetMapping("/signup")
    public String signupPage(HttpSession session, Model model) {
        if (session.getAttribute("user_id") != null) {
            return "redirect:/dashboard";
        }
        model.addAttribute("title", "Create Account - AI Expense Tracker");
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String handleSignup(@RequestParam(name = "name", defaultValue = "") String name,
                               @RequestParam(name = "email", defaultValue = "") String email,
                               @RequestParam(name = "password", defaultValue = "") String password,
                               @RequestParam(name = "confirm_password", defaultValue = "") String confirmPassword,
                               @RequestParam(name = "currency", defaultValue = "₹") String currency,
                               HttpServletRequest request,
                               HttpSession session,
                               Model model) {
        String trimmedName = name.trim();
        String cleanEmail = email.trim().toLowerCase();

        if (trimmedName.length() < 2) {
            FlashHelper.flash(session, "Please enter a valid full name.", "danger");
            model.addAttribute("name", name);
            model.addAttribute("email", email);
            return "auth/signup";
        }

        if (password.length() < 6) {
            FlashHelper.flash(session, "Password must be at least 6 characters long.", "danger");
            model.addAttribute("name", name);
            model.addAttribute("email", email);
            return "auth/signup";
        }

        if (!password.equals(confirmPassword)) {
            FlashHelper.flash(session, "Passwords do not match.", "danger");
            model.addAttribute("name", name);
            model.addAttribute("email", email);
            return "auth/signup";
        }

        User existing = repository.getUserByEmail(cleanEmail);
        if (existing != null) {
            FlashHelper.flash(session, "An account with this email address already exists.", "warning");
            model.addAttribute("name", name);
            model.addAttribute("email", email);
            return "auth/signup";
        }

        Long userId = repository.createUser(trimmedName, cleanEmail, password, currency);
        if (userId != null) {
            session.setAttribute("user_id", userId);
            session.setAttribute("user_name", trimmedName);
            session.setAttribute("user_email", cleanEmail);
            session.setAttribute("user_role", "user");
            session.setAttribute("user_currency", currency);

            String currMonth = YearMonth.now().toString();
            repository.setBudget(userId, "Food", 6000.0, currMonth);
            repository.setBudget(userId, "Bills", 8000.0, currMonth);
            repository.setBudget(userId, "Shopping", 5000.0, currMonth);

            repository.addNotification(userId, "🎉 Welcome to ExpenseAI!",
                    "Your account has been created. Start logging your daily expenses to unlock smart AI insights.", "success");

            String ip = request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
            repository.logAudit("User Registration", userId, cleanEmail, ip);

            FlashHelper.flash(session, "Welcome to ExpenseAI, " + trimmedName + "! Your workspace is ready.", "success");
            return "redirect:/dashboard";
        } else {
            FlashHelper.flash(session, "An error occurred during account creation. Please try again.", "danger");
            return "auth/signup";
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(Model model) {
        model.addAttribute("title", "Reset Password");
        return "auth/forgot_password";
    }

    @PostMapping("/forgot-password")
    public String handleForgotPassword(@RequestParam(name = "email", defaultValue = "") String email,
                                       HttpSession session) {
        FlashHelper.flash(session, "If an account exists for " + email.trim() + ", a secure password reset link has been dispatched.", "info");
        return "redirect:/login";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpSession session) {
        Long uid = (Long) session.getAttribute("user_id");
        String email = (String) session.getAttribute("user_email");
        if (uid != null) {
            String ip = request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
            repository.logAudit("User Logout", uid, email, ip);
        }
        session.invalidate();
        return "redirect:/landing";
    }
}
