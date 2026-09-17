package com.expensetracker.service;

import com.expensetracker.model.Budget;
import com.expensetracker.model.Expense;
import com.expensetracker.model.Recurring;
import com.expensetracker.model.User;
import com.expensetracker.repository.ExpenseRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FinancialIntelligenceService {

    private final ExpenseRepository repository;
    private final CategorizerService categorizerService;
    private final PredictorService predictorService;

    public FinancialIntelligenceService(ExpenseRepository repository,
                                        CategorizerService categorizerService,
                                        PredictorService predictorService) {
        this.repository = repository;
        this.categorizerService = categorizerService;
        this.predictorService = predictorService;
    }

    // ==================== 1. AI FINANCIAL HEALTH SCORE (0 - 100) ====================

    public Map<String, Object> calculateHealthScore(Long userId) {
        User user = repository.getUserById(userId);
        double income = user != null && user.getIncome() != null ? user.getIncome() : 50000.0;
        double monthlyBudget = user != null && user.getMonthlyBudget() != null ? user.getMonthlyBudget() : 25000.0;

        Map<String, Object> summary = repository.getSummary("month", userId);
        double totalSpent = ((Number) summary.get("total")).doubleValue();

        @SuppressWarnings("unchecked")
        Map<String, Double> byCategory = (Map<String, Double>) summary.getOrDefault("by_category", Collections.emptyMap());

        // Factor 1: Savings Rate (0 - 25 points)
        double savingsAmount = Math.max(0.0, income - totalSpent);
        double savingsRate = income > 0 ? (savingsAmount / income) : 0.0;
        double savingsScore = Math.min(25.0, Math.max(0.0, (savingsRate / 0.25) * 25.0));

        // Factor 2: Budget Adherence (0 - 25 points)
        List<Budget> budgets = repository.getUserBudgets(userId, null);
        double budgetScore;
        if (!budgets.isEmpty()) {
            long adherentCount = budgets.stream().filter(b -> !b.getIsExceeded()).count();
            budgetScore = ((double) adherentCount / budgets.size()) * 25.0;
        } else {
            double budgetUtilization = monthlyBudget > 0 ? (totalSpent / monthlyBudget) : 1.0;
            if (budgetUtilization <= 0.8) budgetScore = 25.0;
            else if (budgetUtilization <= 1.0) budgetScore = 20.0;
            else if (budgetUtilization <= 1.15) budgetScore = 10.0;
            else budgetScore = 4.0;
        }

        // Factor 3: Spending Volatility (0 - 20 points)
        Map<String, Double> dailyTotals = repository.getDailyTotalsCurrentMonth(userId);
        double volatilityScore;
        if (dailyTotals.size() >= 3) {
            double sum = 0.0;
            for (double v : dailyTotals.values()) sum += v;
            double avgDaily = sum / dailyTotals.size();

            double variance = 0.0;
            for (double v : dailyTotals.values()) {
                variance += Math.pow(v - avgDaily, 2);
            }
            variance /= dailyTotals.size();
            double stdDev = Math.sqrt(variance);
            double cv = avgDaily > 0 ? (stdDev / avgDaily) : 0.0;

            if (cv < 0.6) volatilityScore = 20.0;
            else if (cv < 1.0) volatilityScore = 16.0;
            else if (cv < 1.5) volatilityScore = 11.0;
            else volatilityScore = 6.0;
        } else {
            volatilityScore = 15.0;
        }

        // Factor 4: Essential vs Discretionary Ratio (0 - 15 points)
        Set<String> essentialCats = Set.of("food", "bills", "health", "education");
        double essentialSpend = 0.0;
        for (Map.Entry<String, Double> e : byCategory.entrySet()) {
            if (essentialCats.contains(e.getKey().toLowerCase())) {
                essentialSpend += e.getValue();
            }
        }
        double discretionarySpend = totalSpent - essentialSpend;
        double discRatio = totalSpent > 0 ? (discretionarySpend / totalSpent) : 0.3;
        double ratioScore;
        if (discRatio <= 0.35) ratioScore = 15.0;
        else if (discRatio <= 0.50) ratioScore = 11.0;
        else if (discRatio <= 0.65) ratioScore = 7.0;
        else ratioScore = 3.0;

        // Factor 5: Recurring Bill Burden (0 - 15 points)
        List<Recurring> recurring = repository.listRecurring(userId);
        double totalRecurring = recurring.stream().filter(Recurring::getIsActive).mapToDouble(Recurring::getAmount).sum();
        double recRatio = income > 0 ? (totalRecurring / income) : 0.2;
        double recurringScore;
        if (recRatio <= 0.25) recurringScore = 15.0;
        else if (recRatio <= 0.40) recurringScore = 10.0;
        else recurringScore = 5.0;

        int totalHealthScore = (int) Math.round(savingsScore + budgetScore + volatilityScore + ratioScore + recurringScore);
        totalHealthScore = Math.max(10, Math.min(99, totalHealthScore));

        String status;
        String statusColor;
        String grade;
        String diagnosis;

        if (totalHealthScore >= 80) {
            status = "Strong & Stable";
            statusColor = "var(--accent)";
            grade = "A";
            diagnosis = "Excellent financial discipline. High savings rate with controlled discretionary outflow.";
        } else if (totalHealthScore >= 65) {
            status = "Healthy";
            statusColor = "#38bdf8";
            grade = "B";
            diagnosis = "Good financial posture. Keep an eye on non-essential impulse spending.";
        } else if (totalHealthScore >= 50) {
            status = "Moderate Risk";
            statusColor = "var(--warning)";
            grade = "C";
            diagnosis = "Spending is approaching safe thresholds. Review subscriptions and top category limits.";
        } else {
            status = "High Vulnerability";
            statusColor = "var(--danger)";
            grade = "D";
            diagnosis = "Outflow is exceeding planned limits. Implement immediate discretionary spending caps.";
        }

        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("savings_rate", Map.of("score", Math.round(savingsScore * 10.0) / 10.0, "max", 25, "label", "Savings Accumulation"));
        factors.put("budget_adherence", Map.of("score", Math.round(budgetScore * 10.0) / 10.0, "max", 25, "label", "Budget Adherence"));
        factors.put("volatility", Map.of("score", Math.round(volatilityScore * 10.0) / 10.0, "max", 20, "label", "Spending Consistency"));
        factors.put("needs_vs_wants", Map.of("score", Math.round(ratioScore * 10.0) / 10.0, "max", 15, "label", "Essential vs Discretionary Ratio"));
        factors.put("recurring_burden", Map.of("score", Math.round(recurringScore * 10.0) / 10.0, "max", 15, "label", "Fixed Obligations Burden"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", totalHealthScore);
        result.put("status", status);
        result.put("status_color", statusColor);
        result.put("grade", grade);
        result.put("diagnosis", diagnosis);
        result.put("savings_rate_pct", Math.round(savingsRate * 1000.0) / 10.0);
        result.put("factors", factors);

        return result;
    }

    // ==================== 2. "CAN I AFFORD THIS?" REAL-TIME ENGINE ====================

    public Map<String, Object> checkAffordability(Long userId, String itemName, double amount, String category) {
        if (category == null || category.isBlank()) {
            category = categorizerService.categorize(itemName);
        }

        User user = repository.getUserById(userId);
        double monthlyBudget = user != null && user.getMonthlyBudget() != null ? user.getMonthlyBudget() : 25000.0;
        String curr = user != null && user.getCurrency() != null ? user.getCurrency() : "₹";

        Map<String, Object> summary = repository.getSummary("month", userId);
        double totalSpent = ((Number) summary.get("total")).doubleValue();

        @SuppressWarnings("unchecked")
        Map<String, Double> byCategory = (Map<String, Double>) summary.getOrDefault("by_category", Collections.emptyMap());
        double categorySpent = byCategory.getOrDefault(category, 0.0);

        LocalDate today = LocalDate.now();
        YearMonth ym = YearMonth.now();
        int daysInMonth = ym.lengthOfMonth();
        int daysLeft = Math.max(1, daysInMonth - today.getDayOfMonth() + 1);

        List<Recurring> recurring = repository.listRecurring(userId);
        double upcomingBills = 0.0;
        for (Recurring r : recurring) {
            if (r.getIsActive() && r.getNextDueDate() != null) {
                try {
                    LocalDate dueD = LocalDate.parse(r.getNextDueDate());
                    if (!dueD.isBefore(today) && dueD.getMonth() == today.getMonth()) {
                        upcomingBills += r.getAmount();
                    }
                } catch (Exception ignored) {}
            }
        }

        double safePool = Math.max(0.0, monthlyBudget - totalSpent - upcomingBills);
        double safeDailyBefore = safePool / daysLeft;
        double safePoolAfter = Math.max(0.0, safePool - amount);
        double safeDailyAfter = safePoolAfter / daysLeft;

        List<Budget> budgets = repository.getUserBudgets(userId, null);
        final String finalCategory = category;
        Budget catBudget = budgets.stream()
                .filter(b -> b.getCategory().equalsIgnoreCase(finalCategory))
                .findFirst()
                .orElse(null);

        double catLimit = catBudget != null ? catBudget.getLimitAmount() : (monthlyBudget * 0.3);
        double catSpentAfter = categorySpent + amount;
        boolean catExceeded = catSpentAfter > catLimit;

        String verdict;
        String verdictBadge;
        int riskScore;
        String recommendation;

        if (amount <= (safePool * 0.35) && !catExceeded) {
            verdict = "Affordable ✅";
            verdictBadge = "success";
            riskScore = 15;
            recommendation = String.format("This purchase fits comfortably within your monthly budget buffer. You will still have %s%.2f safe discretionary funds remaining.", curr, safePoolAfter);
        } else if (amount <= safePool && (catSpentAfter <= catLimit * 1.1)) {
            verdict = "Caution ⚠️";
            verdictBadge = "warning";
            riskScore = 55;
            int pct = safePool > 0 ? (int) Math.round((amount / safePool) * 100.0) : 100;
            recommendation = String.format("This purchase is feasible today, but it consumes %d%% of your remaining buffer. Your safe daily allowance will reduce from %s%.0f/day to %s%.0f/day.",
                    pct, curr, safeDailyBefore, curr, safeDailyAfter);
        } else {
            verdict = "High Financial Risk 🚨";
            verdictBadge = "danger";
            riskScore = 88;
            double shortfall = amount - safePool;
            recommendation = String.format("This purchase would exceed your safe discretionary pool by %s%.2f and risk a cash-flow shortage against upcoming bills. Consider delaying or saving for it.", curr, shortfall);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("item_name", itemName);
        result.put("amount", amount);
        result.put("category", category);
        result.put("verdict", verdict);
        result.put("verdict_badge", verdictBadge);
        result.put("risk_score", riskScore);
        result.put("safe_pool_before", Math.round(safePool * 100.0) / 100.0);
        result.put("safe_pool_after", Math.round(safePoolAfter * 100.0) / 100.0);
        result.put("safe_daily_before", Math.round(safeDailyBefore * 100.0) / 100.0);
        result.put("safe_daily_after", Math.round(safeDailyAfter * 100.0) / 100.0);
        result.put("upcoming_bills", Math.round(upcomingBills * 100.0) / 100.0);
        result.put("days_left", daysLeft);
        result.put("recommendation", recommendation);

        return result;
    }

    // ==================== 3. CASH-FLOW COLLISION PREDICTOR ====================

    public List<Map<String, Object>> detectCashflowCollisions(Long userId) {
        User user = repository.getUserById(userId);
        double monthlyBudget = user != null && user.getMonthlyBudget() != null ? user.getMonthlyBudget() : 25000.0;
        String curr = user != null && user.getCurrency() != null ? user.getCurrency() : "₹";

        Map<String, Object> summary = repository.getSummary("month", userId);
        double totalSpent = ((Number) summary.get("total")).doubleValue();
        double dailyAvg = ((Number) summary.get("daily_average")).doubleValue();
        double avgDailyBurn = dailyAvg > 0 ? dailyAvg : 400.0;

        List<Recurring> activeBills = repository.listRecurring(userId).stream().filter(Recurring::getIsActive).toList();
        List<Map<String, Object>> collisions = new ArrayList<>();

        LocalDate today = LocalDate.now();
        double runningPool = Math.max(0.0, monthlyBudget - totalSpent);

        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM");
        DateTimeFormatter shortDateFormatter = DateTimeFormatter.ofPattern("MMM dd");

        for (int dayOffset = 1; dayOffset <= 20; dayOffset++) {
            LocalDate futureDate = today.plusDays(dayOffset);
            runningPool -= avgDailyBurn;

            List<Recurring> billsToday = new ArrayList<>();
            for (Recurring b : activeBills) {
                if (b.getNextDueDate() != null) {
                    try {
                        LocalDate due = LocalDate.parse(b.getNextDueDate());
                        if (due.getDayOfMonth() == futureDate.getDayOfMonth() && due.getMonth() == futureDate.getMonth()) {
                            billsToday.add(b);
                        }
                    } catch (Exception ignored) {}
                }
            }

            for (Recurring bill : billsToday) {
                double billAmt = bill.getAmount();
                if (runningPool < billAmt) {
                    double shortfall = billAmt - Math.max(0.0, runningPool);
                    Map<String, Object> col = new LinkedHashMap<>();
                    col.put("date", futureDate.toString());
                    col.put("day_name", futureDate.format(dayFormatter));
                    col.put("days_until", dayOffset);
                    col.put("days_remaining", dayOffset);
                    col.put("bill_name", bill.getDescription());
                    col.put("bill_amount", billAmt);
                    col.put("projected_pool", Math.round(Math.max(0.0, runningPool) * 100.0) / 100.0);
                    col.put("shortfall", Math.round(shortfall * 100.0) / 100.0);
                    col.put("severity", shortfall > 2000 ? "high" : "medium");
                    col.put("message", String.format("Projected balance (%s%.0f) may not cover %s (%s%.0f) on %s. Estimated shortfall: %s%.0f.",
                            curr, Math.max(0.0, runningPool), bill.getDescription(), curr, billAmt, futureDate.format(shortDateFormatter), curr, shortfall));
                    collisions.add(col);
                }
                runningPool -= billAmt;
            }
        }

        return collisions;
    }

    // ==================== 4. MONEY LEAK DETECTOR ====================

    public Map<String, Object> detectMoneyLeaks(Long userId, double threshold) {
        User user = repository.getUserById(userId);
        String curr = user != null && user.getCurrency() != null ? user.getCurrency() : "₹";

        String firstDay = LocalDate.now().withDayOfMonth(1).toString();
        List<Expense> expenses = repository.listExpenses(userId, firstDay, null, null, null, null, "date_desc", 500, 0);

        List<Expense> microSpends = expenses.stream().filter(e -> e.getAmount() <= threshold).toList();
        double totalMicroAmount = microSpends.stream().mapToDouble(Expense::getAmount).sum();
        double totalMonthSpend = expenses.stream().mapToDouble(Expense::getAmount).sum();

        double leakPct = totalMonthSpend > 0 ? (totalMicroAmount / totalMonthSpend * 100.0) : 0.0;

        Map<String, Double> catLeaks = new HashMap<>();
        for (Expense e : microSpends) {
            catLeaks.put(e.getCategory(), catLeaks.getOrDefault(e.getCategory(), 0.0) + e.getAmount());
        }

        String topLeakCategory = catLeaks.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");

        double annualProjected = totalMicroAmount * 12.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("micro_spend_count", microSpends.size());
        result.put("total_micro_amount", Math.round(totalMicroAmount * 100.0) / 100.0);
        result.put("leak_pct", Math.round(leakPct * 10.0) / 10.0);
        result.put("top_leak_category", topLeakCategory);
        result.put("annual_projected", Math.round(annualProjected * 100.0) / 100.0);
        result.put("leak_items", microSpends.size() > 10 ? microSpends.subList(0, 10) : microSpends);
        result.put("recommendation", String.format("You made %d small purchases below %s%.0f this month totaling %s%.2f (%d%% of total spend). Cutting 30%% of these small convenience fees could save %s%.0f/year.",
                microSpends.size(), curr, threshold, curr, totalMicroAmount, (int) Math.round(leakPct), curr, annualProjected * 0.3));

        return result;
    }

    // ==================== 5. SAFE DAILY SPENDING LIMIT ====================

    public Map<String, Object> calculateSafeDailySpend(Long userId) {
        User user = repository.getUserById(userId);
        double monthlyBudget = user != null && user.getMonthlyBudget() != null ? user.getMonthlyBudget() : 25000.0;

        Map<String, Object> summary = repository.getSummary("month", userId);
        double totalSpent = ((Number) summary.get("total")).doubleValue();

        LocalDate today = LocalDate.now();
        YearMonth ym = YearMonth.now();
        int daysInMonth = ym.lengthOfMonth();
        int daysLeft = Math.max(1, daysInMonth - today.getDayOfMonth() + 1);

        List<Recurring> recurring = repository.listRecurring(userId);
        double upcomingBills = recurring.stream().filter(Recurring::getIsActive).mapToDouble(Recurring::getAmount).sum();

        double safePool = Math.max(0.0, monthlyBudget - totalSpent - (upcomingBills * 0.5));
        double safeDaily = safePool / daysLeft;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("safe_daily_spend", Math.round(safeDaily * 100.0) / 100.0);
        result.put("safe_pool", Math.round(safePool * 100.0) / 100.0);
        result.put("days_left", daysLeft);
        result.put("monthly_budget", monthlyBudget);
        result.put("total_spent", Math.round(totalSpent * 100.0) / 100.0);
        result.put("upcoming_bills", Math.round(upcomingBills * 100.0) / 100.0);

        return result;
    }

    // ==================== 6. FINANCIAL "WHAT-IF" SIMULATOR ====================

    public Map<String, Object> simulateWhatIf(Long userId, String scenarioType, Map<String, Object> params) {
        User user = repository.getUserById(userId);
        double income = user != null && user.getIncome() != null ? user.getIncome() : 50000.0;
        String curr = user != null && user.getCurrency() != null ? user.getCurrency() : "₹";

        Map<String, Object> forecast = predictorService.predictNextMonth(6, userId);
        double baselineMonthlySpend = ((Number) forecast.get("total_next_month")).doubleValue();
        if (baselineMonthlySpend <= 0) baselineMonthlySpend = 20000.0;
        double baselineMonthlySavings = Math.max(0.0, income - baselineMonthlySpend);

        List<String> months = List.of("Month 1", "Month 2", "Month 3", "Month 4", "Month 5", "Month 6");

        if ("major_purchase".equalsIgnoreCase(scenarioType)) {
            double purchaseCost = 50000.0;
            if (params != null && params.get("amount") != null) {
                purchaseCost = ((Number) params.get("amount")).doubleValue();
            }
            String itemName = (params != null && params.get("item_name") != null) ? params.get("item_name").toString() : "Major Purchase";

            List<Double> baseline = new ArrayList<>();
            List<Double> revised = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                baseline.add(Math.round(baselineMonthlySavings * i * 100.0) / 100.0);
                revised.add(Math.round(Math.max(0.0, (baselineMonthlySavings * i) - purchaseCost) * 100.0) / 100.0);
            }

            double delayMonths = baselineMonthlySavings > 0 ? (purchaseCost / baselineMonthlySavings) : 6.0;
            delayMonths = Math.round(delayMonths * 10.0) / 10.0;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("scenario_title", String.format("Purchase Impact: %s (%s%,.0f)", itemName, curr, purchaseCost));
            result.put("months", months);
            result.put("baseline_trajectory", baseline);
            result.put("revised_trajectory", revised);
            result.put("savings_delay", String.format("This purchase will set back your savings accumulation by approximately %.1f months.", delayMonths));
            result.put("monthly_impact", String.format("Month 1 net savings will decrease from %s%,.0f to %s%,.0f.", curr, baselineMonthlySavings, curr, Math.max(0.0, baselineMonthlySavings - purchaseCost)));
            return result;
        } else if ("habit_change".equalsIgnoreCase(scenarioType)) {
            double dailySave = 100.0;
            if (params != null && params.get("daily_savings") != null) {
                dailySave = ((Number) params.get("daily_savings")).doubleValue();
            }
            double monthlyGain = dailySave * 30.0;
            double annualGain = dailySave * 365.0;

            List<Double> baseline = new ArrayList<>();
            List<Double> revised = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                baseline.add(Math.round(baselineMonthlySavings * i * 100.0) / 100.0);
                revised.add(Math.round((baselineMonthlySavings + monthlyGain) * i * 100.0) / 100.0);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("scenario_title", String.format("Habit Change: Save %s%.0f/day", curr, dailySave));
            result.put("months", months);
            result.put("baseline_trajectory", baseline);
            result.put("revised_trajectory", revised);
            result.put("savings_delay", String.format("Saving %s%.0f/day yields an extra %s%,.0f/month and %s%,.0f/year.", curr, dailySave, curr, monthlyGain, curr, annualGain));
            result.put("monthly_impact", String.format("Increases your monthly savings rate by %.1f%%.", income > 0 ? (monthlyGain / income * 100.0) : 0.0));
            return result;
        } else {
            double costIncrease = 2000.0;
            if (params != null && params.get("cost_increase") != null) {
                costIncrease = ((Number) params.get("cost_increase")).doubleValue();
            }
            double revisedSavings = Math.max(0.0, baselineMonthlySavings - costIncrease);

            List<Double> baseline = new ArrayList<>();
            List<Double> revised = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                baseline.add(Math.round(baselineMonthlySavings * i * 100.0) / 100.0);
                revised.add(Math.round(revisedSavings * i * 100.0) / 100.0);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("scenario_title", String.format("Cost Increase: +%s%.0f/month", curr, costIncrease));
            result.put("months", months);
            result.put("baseline_trajectory", baseline);
            result.put("revised_trajectory", revised);
            result.put("savings_delay", String.format("Reduces your 6-month wealth creation by %s%,.0f.", curr, costIncrease * 6.0));
            result.put("monthly_impact", String.format("Reduces projected monthly savings from %s%,.0f to %s%,.0f.", curr, baselineMonthlySavings, curr, revisedSavings));
            return result;
        }
    }

    // ==================== 7. SMART RECEIPT TEXT PARSER ====================

    public Map<String, Object> parseReceiptText(String text) {
        if (text == null) text = "";
        String cleanText = text.trim();

        // Extract total
        double total = 0.0;
        List<String> patterns = List.of(
                "(?i)(?:total|grand\\s+total|amount\\s+due|net\\s+amount|bill\\s+amount)[\\s:=₹rs\\.]*([\\d,]+\\.?\\d*)",
                "(?i)(?:₹|rs\\.?)\\s*([\\d,]+\\.?\\d*)",
                "([\\d,]+\\.\\d{2})"
        );

        for (String pat : patterns) {
            Pattern p = Pattern.compile(pat);
            Matcher m = p.matcher(cleanText);
            if (m.find()) {
                try {
                    String rawNum = m.group(1).replace(",", "");
                    double parsed = Double.parseDouble(rawNum);
                    if (parsed > 0) {
                        total = parsed;
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }

        // Extract date
        String transDate = LocalDate.now().toString();
        Pattern datePattern = Pattern.compile("(\\d{4}[-/]\\d{2}[-/]\\d{2}|\\d{2}[-/]\\d{2}[-/]\\d{4})");
        Matcher dateMatcher = datePattern.matcher(cleanText);
        if (dateMatcher.find()) {
            String rawD = dateMatcher.group(1).replace("/", "-");
            try {
                String[] parts = rawD.split("-");
                if (parts[0].length() == 4) {
                    transDate = rawD;
                } else {
                    transDate = String.format("%s-%s-%s", parts[2], parts[1], parts[0]);
                }
            } catch (Exception ignored) {}
        }

        // Extract merchant name
        String[] lines = cleanText.split("\\R");
        String merchant = "Store Receipt";
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                merchant = line.trim();
                break;
            }
        }
        if (merchant.length() > 35) {
            merchant = merchant.substring(0, 35);
        }

        String category = categorizerService.categorize(cleanText);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("merchant", merchant);
        result.put("amount", Math.round(total * 100.0) / 100.0);
        result.put("date", transDate);
        result.put("category", category);
        result.put("raw_text", cleanText);

        return result;
    }
}
