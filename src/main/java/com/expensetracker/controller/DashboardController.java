package com.expensetracker.controller;

import com.expensetracker.model.Budget;
import com.expensetracker.model.Expense;
import com.expensetracker.model.Recurring;
import com.expensetracker.model.User;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.service.CategorizerService;
import com.expensetracker.service.FinancialIntelligenceService;
import com.expensetracker.service.PredictorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
public class DashboardController {

    private final ExpenseRepository repository;
    private final CategorizerService categorizerService;
    private final PredictorService predictorService;
    private final FinancialIntelligenceService financialService;

    public DashboardController(ExpenseRepository repository,
                               CategorizerService categorizerService,
                               PredictorService predictorService,
                               FinancialIntelligenceService financialService) {
        this.repository = repository;
        this.categorizerService = categorizerService;
        this.predictorService = predictorService;
        this.financialService = financialService;
    }

    @GetMapping("/")
    public String index(HttpSession session) {
        if (session.getAttribute("user_id") != null) {
            return "redirect:/dashboard";
        }
        return "landing";
    }

    @GetMapping("/landing")
    public String landing(Model model) {
        model.addAttribute("title", "AI Expense Tracker - Predictive Finance");
        return "landing";
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Long uid = (Long) session.getAttribute("user_id");
        User user = repository.getUserById(uid);
        String curr = user != null && user.getCurrency() != null ? user.getCurrency() : "₹";
        session.setAttribute("user_currency", curr);

        Map<String, Object> summaryMonth = repository.getSummary("month", uid);
        List<Expense> recentExpenses = repository.listRecentExpenses(uid, 6);
        Map<String, Object> forecast = predictorService.predictNextMonth(6, uid);
        List<Budget> budgets = repository.getUserBudgets(uid, null);
        List<Recurring> upcomingBills = repository.listRecurring(uid);

        Map<String, Object> healthScore = financialService.calculateHealthScore(uid);
        Map<String, Object> safeDailySpend = financialService.calculateSafeDailySpend(uid);
        List<Map<String, Object>> cashflowAlerts = financialService.detectCashflowCollisions(uid);

        double userMonthlyBudget = user != null && user.getMonthlyBudget() != null ? user.getMonthlyBudget() : 25000.0;
        double monthTotal = ((Number) summaryMonth.get("total")).doubleValue();
        double budgetRemaining = Math.max(0.0, userMonthlyBudget - monthTotal);
        double budgetUsedPct = userMonthlyBudget > 0 ? Math.round((monthTotal / userMonthlyBudget * 100.0) * 10.0) / 10.0 : 0.0;

        Map<String, Double> monthlyMap = repository.monthlyTotals(uid);
        List<String> allMonthlyKeys = new ArrayList<>(monthlyMap.keySet());
        Collections.sort(allMonthlyKeys);

        List<String> monthlyTrendLabels;
        List<Double> monthlyTrendValues = new ArrayList<>();
        if (!allMonthlyKeys.isEmpty()) {
            int start = Math.max(0, allMonthlyKeys.size() - 6);
            monthlyTrendLabels = allMonthlyKeys.subList(start, allMonthlyKeys.size());
            for (String k : monthlyTrendLabels) {
                monthlyTrendValues.add(monthlyMap.getOrDefault(k, 0.0));
            }
        } else {
            monthlyTrendLabels = List.of(YearMonth.now().toString());
            monthlyTrendValues.add(0.0);
        }

        @SuppressWarnings("unchecked")
        Map<String, Double> byCat = (Map<String, Double>) summaryMonth.getOrDefault("by_category", Collections.emptyMap());
        List<String> categoryChartLabels = new ArrayList<>(byCat.keySet());
        List<Double> categoryChartValues = new ArrayList<>(byCat.values());
        List<String> categoryChartColors = new ArrayList<>();
        for (String c : categoryChartLabels) {
            Map<String, String> meta = categorizerService.getMeta(c);
            categoryChartColors.add(meta.getOrDefault("color", "#6366f1"));
        }

        LocalDate today = LocalDate.now();
        DateTimeFormatter monthNameFormatter = DateTimeFormatter.ofPattern("MMMM yyyy");

        model.addAttribute("title", "Dashboard - AI Expense Tracker");
        model.addAttribute("user", user);
        model.addAttribute("summary", summaryMonth);
        model.addAttribute("recent_expenses", recentExpenses);
        model.addAttribute("forecast", forecast);
        model.addAttribute("budgets", budgets);
        model.addAttribute("upcoming_bills", upcomingBills);
        model.addAttribute("health_score", healthScore);
        model.addAttribute("safe_daily_spend", safeDailySpend);
        model.addAttribute("cashflow_alerts", cashflowAlerts);
        model.addAttribute("user_monthly_budget", userMonthlyBudget);
        model.addAttribute("budget_remaining", budgetRemaining);
        model.addAttribute("budget_used_pct", budgetUsedPct);
        model.addAttribute("day_of_month", today.getDayOfMonth());
        model.addAttribute("current_month_name", today.format(monthNameFormatter));
        model.addAttribute("monthly_trend_labels", monthlyTrendLabels);
        model.addAttribute("monthly_trend_values", monthlyTrendValues);
        model.addAttribute("category_chart_labels", categoryChartLabels);
        model.addAttribute("category_chart_values", categoryChartValues);
        model.addAttribute("category_chart_colors", categoryChartColors);

        return "index";
    }

    @GetMapping("/decision-studio")
    public String decisionStudioPage(HttpSession session, Model model) {
        Long uid = (Long) session.getAttribute("user_id");
        User user = repository.getUserById(uid);

        Map<String, Object> healthScore = financialService.calculateHealthScore(uid);
        Map<String, Object> safeDailySpend = financialService.calculateSafeDailySpend(uid);
        List<Map<String, Object>> cashflowCollisions = financialService.detectCashflowCollisions(uid);
        Map<String, Object> moneyLeaks = financialService.detectMoneyLeaks(uid, 300.0);

        model.addAttribute("title", "Decision & Risk Studio - ExpenseAI");
        model.addAttribute("user", user);
        model.addAttribute("health_score", healthScore);
        model.addAttribute("safe_daily_spend", safeDailySpend);
        model.addAttribute("cashflow_collisions", cashflowCollisions);
        model.addAttribute("money_leaks", moneyLeaks);

        return "decision_studio";
    }
}
