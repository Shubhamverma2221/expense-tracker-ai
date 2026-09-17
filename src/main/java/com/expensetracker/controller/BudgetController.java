package com.expensetracker.controller;

import com.expensetracker.config.FlashHelper;
import com.expensetracker.model.Budget;
import com.expensetracker.model.Recurring;
import com.expensetracker.repository.ExpenseRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class BudgetController {

    private final ExpenseRepository repository;

    public BudgetController(ExpenseRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/budgets")
    public String budgetsPage(HttpSession session, Model model) {
        Long uid = (Long) session.getAttribute("user_id");
        String currMonth = YearMonth.now().toString();

        List<Budget> budgets = repository.getUserBudgets(uid, currMonth);
        List<Recurring> recurring = repository.listRecurring(uid);

        double totalAllocatedBudget = budgets.stream().mapToDouble(Budget::getLimitAmount).sum();
        double totalBudgetSpent = budgets.stream().mapToDouble(Budget::getSpent).sum();
        double totalBudgetRemaining = Math.max(0.0, totalAllocatedBudget - totalBudgetSpent);
        double budgetUtilizationPct = totalAllocatedBudget > 0 ? Math.round((totalBudgetSpent / totalAllocatedBudget * 100.0) * 10.0) / 10.0 : 0.0;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy");

        model.addAttribute("title", "Budgets & Recurring");
        model.addAttribute("budgets", budgets);
        model.addAttribute("recurring", recurring);
        model.addAttribute("current_month", currMonth);
        model.addAttribute("current_month_str", LocalDate.now().format(formatter));
        model.addAttribute("total_allocated_budget", Math.round(totalAllocatedBudget * 100.0) / 100.0);
        model.addAttribute("total_budget_spent", Math.round(totalBudgetSpent * 100.0) / 100.0);
        model.addAttribute("total_budget_remaining", Math.round(totalBudgetRemaining * 100.0) / 100.0);
        model.addAttribute("budget_utilization_pct", budgetUtilizationPct);

        return "budgets";
    }

    @PostMapping("/budgets")
    public String handleBudgetsPost(@RequestParam(name = "action", defaultValue = "set_budget") String action,
                                   @RequestParam(name = "category", required = false) String category,
                                   @RequestParam(name = "limit_amount", required = false) Double limitAmount,
                                   @RequestParam(name = "description", required = false) String description,
                                   @RequestParam(name = "amount", required = false) Double amount,
                                   @RequestParam(name = "frequency", defaultValue = "Monthly") String frequency,
                                   @RequestParam(name = "next_due_date", required = false) String nextDueDate,
                                   HttpSession session) {
        Long uid = (Long) session.getAttribute("user_id");
        String currMonth = YearMonth.now().toString();
        String curr = session.getAttribute("user_currency") != null ? String.valueOf(session.getAttribute("user_currency")) : "₹";

        if ("set_budget".equalsIgnoreCase(action)) {
            if (category != null && !category.isBlank() && limitAmount != null && limitAmount > 0) {
                repository.setBudget(uid, category.trim(), limitAmount, currMonth);
                FlashHelper.flash(session, String.format("Monthly budget for %s set to %s%.2f.", category.trim(), curr, limitAmount), "success");
            }
        } else if ("add_recurring".equalsIgnoreCase(action)) {
            if (description != null && !description.isBlank() && amount != null && amount > 0) {
                String cat = category != null && !category.isBlank() ? category.trim() : "Bills";
                String due = (nextDueDate != null && !nextDueDate.isBlank()) ? nextDueDate : LocalDate.now().toString();
                repository.addRecurring(uid, description.trim(), amount, cat, frequency, due);
                FlashHelper.flash(session, String.format("Recurring subscription for '%s' added!", description.trim()), "success");
            }
        }

        return "redirect:/budgets";
    }

    @PostMapping("/budgets/delete/{budgetId}")
    public String deleteBudget(@PathVariable("budgetId") Long budgetId, HttpSession session) {
        Long uid = (Long) session.getAttribute("user_id");
        repository.deleteBudget(budgetId, uid);
        FlashHelper.flash(session, "Category budget removed.", "info");
        return "redirect:/budgets";
    }

    @PostMapping("/recurring/delete/{recurringId}")
    public String deleteRecurring(@PathVariable("recurringId") Long recurringId, HttpSession session) {
        Long uid = (Long) session.getAttribute("user_id");
        repository.deleteRecurring(recurringId, uid);
        FlashHelper.flash(session, "Recurring payment removed.", "info");
        return "redirect:/budgets";
    }
}
