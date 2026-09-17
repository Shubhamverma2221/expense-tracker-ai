package com.expensetracker.controller;

import com.expensetracker.config.FlashHelper;
import com.expensetracker.model.Notification;
import com.expensetracker.model.User;
import com.expensetracker.repository.ExpenseRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class UserController {

    private final ExpenseRepository repository;

    public UserController(ExpenseRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/profile")
    public String profilePage(HttpSession session, Model model) {
        Long uid = (Long) session.getAttribute("user_id");
        User user = repository.getUserById(uid);
        model.addAttribute("title", "User Profile");
        model.addAttribute("user", user);
        return "profile";
    }

    @PostMapping("/profile")
    public String handleProfileUpdate(@RequestParam(name = "name", defaultValue = "") String name,
                                      @RequestParam(name = "currency", defaultValue = "₹") String currency,
                                      @RequestParam(name = "monthly_budget", defaultValue = "25000") double monthlyBudget,
                                      @RequestParam(name = "income", defaultValue = "50000") double income,
                                      @RequestParam(name = "savings_goal", defaultValue = "100000") double savingsGoal,
                                      @RequestParam(name = "is_student_mode", required = false) String isStudentModeStr,
                                      @RequestParam(name = "monthly_allowance", defaultValue = "15000") double monthlyAllowance,
                                      @RequestParam(name = "theme", defaultValue = "light") String theme,
                                      HttpSession session) {
        Long uid = (Long) session.getAttribute("user_id");
        int isStudent = "1".equals(isStudentModeStr) ? 1 : 0;
        String trimmedName = name.trim();

        repository.updateUserProfile(uid, trimmedName, currency, monthlyBudget, income, savingsGoal, isStudent, monthlyAllowance, theme);

        session.setAttribute("user_name", trimmedName);
        session.setAttribute("user_currency", currency);

        FlashHelper.flash(session, "Profile & Financial Baselines updated successfully!", "success");
        return "redirect:/profile";
    }

    @GetMapping("/settings")
    public String settingsPage(Model model) {
        model.addAttribute("title", "Account Settings");
        return "settings";
    }

    @PostMapping("/settings/change-password")
    public String changePassword(@RequestParam(name = "new_password", defaultValue = "") String newPwd,
                                 @RequestParam(name = "confirm_password", defaultValue = "") String confirmPwd,
                                 HttpSession session) {
        Long uid = (Long) session.getAttribute("user_id");

        if (newPwd.length() < 6 || !newPwd.equals(confirmPwd)) {
            FlashHelper.flash(session, "Passwords must match and have at least 6 characters.", "danger");
            return "redirect:/settings";
        }

        repository.updateUserPassword(uid, newPwd);
        FlashHelper.flash(session, "Password updated successfully!", "success");
        return "redirect:/settings";
    }

    @PostMapping("/settings/delete-account")
    public String deleteAccount(HttpSession session) {
        Long uid = (Long) session.getAttribute("user_id");
        repository.deleteUser(uid);
        session.invalidate();
        return "redirect:/landing";
    }

    @GetMapping("/notifications")
    public String notificationsPage(HttpSession session, Model model) {
        Long uid = (Long) session.getAttribute("user_id");
        List<Notification> notifs = repository.getNotifications(uid, 30);

        model.addAttribute("title", "Notifications");
        model.addAttribute("notifications", notifs);
        return "notifications";
    }

    @PostMapping("/notifications/read-all")
    public String markAllNotificationsRead(HttpSession session) {
        Long uid = (Long) session.getAttribute("user_id");
        repository.markAllNotificationsRead(uid);
        FlashHelper.flash(session, "All notifications marked as read.", "info");
        return "redirect:/notifications";
    }

    @PostMapping("/notifications/clear")
    public String clearNotifications(HttpSession session) {
        Long uid = (Long) session.getAttribute("user_id");
        repository.clearAllNotifications(uid);
        FlashHelper.flash(session, "Notification inbox cleared.", "info");
        return "redirect:/notifications";
    }
}
