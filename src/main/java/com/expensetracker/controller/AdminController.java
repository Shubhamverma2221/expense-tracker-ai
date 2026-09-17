package com.expensetracker.controller;

import com.expensetracker.repository.ExpenseRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@Controller
public class AdminController {

    private final ExpenseRepository repository;

    public AdminController(ExpenseRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/admin")
    public String adminPage(Model model) {
        Map<String, Object> metrics = repository.getAdminMetrics();
        model.addAttribute("title", "Admin Dashboard");
        model.addAttribute("admin_metrics", metrics);
        return "admin";
    }
}
