package com.expensetracker.service;

import com.expensetracker.model.User;
import com.expensetracker.repository.ExpenseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatBotService {

    private final ExpenseRepository repository;
    private final CategorizerService categorizerService;
    private final PredictorService predictorService;
    private final FinancialIntelligenceService financialService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${gemini.api.key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    public ChatBotService(ExpenseRepository repository,
                          CategorizerService categorizerService,
                          PredictorService predictorService,
                          FinancialIntelligenceService financialService) {
        this.repository = repository;
        this.categorizerService = categorizerService;
        this.predictorService = predictorService;
        this.financialService = financialService;
    }

    public String respond(String message, Long userId, String currency) {
        if (message == null || message.trim().isEmpty()) {
            return "Please enter a question or command. Type 'help' for examples.";
        }
        String cleanText = message.trim();
        String curr = currency != null ? currency : "₹";
        Long uid = userId != null ? userId : 1L;

        // 1. Affordability
        String affordabilityReply = parseAffordabilityIntent(cleanText, uid, curr);
        if (affordabilityReply != null) return affordabilityReply;

        // 2. Health Score
        String healthReply = parseHealthScoreIntent(cleanText, uid);
        if (healthReply != null) return healthReply;

        // 3. Money Leaks
        String leaksReply = parseMoneyLeaksIntent(cleanText, uid, curr);
        if (leaksReply != null) return leaksReply;

        // 4. Safe Daily
        String safeDailyReply = parseSafeDailyIntent(cleanText, uid, curr);
        if (safeDailyReply != null) return safeDailyReply;

        // 5. Cashflow Collision
        String collisionReply = parseCashflowCollisionIntent(cleanText, uid, curr);
        if (collisionReply != null) return collisionReply;

        // 6. What-If Scenarios
        String whatIfReply = parseWhatIfIntent(cleanText, uid, curr);
        if (whatIfReply != null) return whatIfReply;

        // 7. Add Transaction
        String addReply = parseAddIntent(cleanText, uid, curr);
        if (addReply != null) return addReply;

        // 8. Budget Status
        String budgetReply = parseBudgetIntent(cleanText, uid, curr);
        if (budgetReply != null) return budgetReply;

        // 9. Prediction
        String predictReply = parsePredictIntent(cleanText, uid, curr);
        if (predictReply != null) return predictReply;

        // 10. Gemini LLM fallback
        String geminiReply = generateGeminiResponse(cleanText, uid, curr);
        if (geminiReply != null && !geminiReply.isBlank()) {
            return geminiReply;
        }

        // 11. Helpful guidance default
        return """
            🤖 **AI Copilot Ready:**
            • *'Can I afford headphones for ₹4,500?'*
            • *'What is my health score?'*
            • *'Where am I leaking money?'*
            • *'Safe daily limit'*
            • *'How can I save ₹20,000 in 3 months?'*
            • *'What if I save ₹150 every day?'*
            """;
    }

    private String parseAffordabilityIntent(String text, Long userId, String curr) {
        String lowered = text.toLowerCase();
        List<String> triggers = List.of("can i afford", "should i buy", "can i buy", "afford to buy", "can i get", "is it safe to buy");
        if (triggers.stream().noneMatch(lowered::contains)) {
            return null;
        }

        Pattern amtPat = Pattern.compile("(?:₹|rs\\.?|\\$|€|£)?\\s*(\\d+(?:,\\d{3})*(?:\\.\\d{1,2})?)");
        Matcher amtMatch = amtPat.matcher(lowered);
        if (!amtMatch.find()) {
            return "💡 Please include the item price (e.g. *'Can I afford shoes for ₹3,500?'*).";
        }

        double amount = Double.parseDouble(amtMatch.group(1).replace(",", ""));
        String itemName = "Purchase Item";

        Pattern itemPat = Pattern.compile("(?i)(?:buy|afford|get)\\s+(?:a|an|the)?\\s*(.+?)\\s+(?:for|costing|worth|at|priced)\\s+(?:₹|rs\\.?|\\$|€|£)?\\s*\\d+");
        Matcher itemMatch = itemPat.matcher(text);
        if (itemMatch.find()) {
            itemName = itemMatch.group(1).trim();
        } else {
            itemName = text.replaceAll("(?i)(?:can i afford|should i buy|can i buy|for|₹|rs|\\d+)", "").trim();
            if (itemName.isEmpty()) itemName = "Item";
        }

        Map<String, Object> res = financialService.checkAffordability(userId, itemName, amount, null);

        return String.format("""
            ### 🔍 Affordability Decision: **%s** (%s%,.2f)
            • **Verdict:** **%s** (Risk Score: %s/100)
            • **Category:** %s
            • **Discretionary Buffer Before:** %s%,.2f
            • **Discretionary Buffer After:** %s%,.2f
            • **Safe Daily Allowance Impact:** %s%,.0f/day ➔ **%s%,.0f/day**

            💡 **Recommendation:**
            %s
            """,
                res.get("item_name"), curr, amount,
                res.get("verdict"), res.get("risk_score"),
                res.get("category"),
                curr, res.get("safe_pool_before"),
                curr, res.get("safe_pool_after"),
                curr, res.get("safe_daily_before"), curr, res.get("safe_daily_after"),
                res.get("recommendation")
        );
    }

    private String parseHealthScoreIntent(String text, Long userId) {
        String lowered = text.toLowerCase();
        List<String> triggers = List.of("health score", "financial health", "my score", "financial stability", "financial rating");
        if (triggers.stream().noneMatch(lowered::contains)) {
            return null;
        }

        Map<String, Object> h = financialService.calculateHealthScore(userId);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("### 🧠 AI Financial Health Rating: **%s/100** (%s - Grade %s)%n", h.get("score"), h.get("status"), h.get("grade")));
        sb.append(String.format("• **Savings Rate:** %s%%%n", h.get("savings_rate_pct")));
        sb.append(String.format("• **Diagnosis:** %s%n%n", h.get("diagnosis")));
        sb.append("**Factor Breakdown:**\n");

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> factors = (Map<String, Map<String, Object>>) h.get("factors");
        if (factors != null) {
            for (Map<String, Object> f : factors.values()) {
                sb.append(String.format("• %s: **%s/%s pts**%n", f.get("label"), f.get("score"), f.get("max")));
            }
        }
        return sb.toString().trim();
    }

    private String parseMoneyLeaksIntent(String text, Long userId, String curr) {
        String lowered = text.toLowerCase();
        List<String> triggers = List.of("money leak", "leaking money", "small expenses", "micro spend", "hidden leak", "micro-spend");
        if (triggers.stream().noneMatch(lowered::contains)) {
            return null;
        }

        Map<String, Object> leaks = financialService.detectMoneyLeaks(userId, 300.0);
        return String.format("""
            ### 🕵️ Money Leak Audit (< %s300 Micro-Spends)
            • **Micro-Transactions Logged:** %s purchases
            • **Total Micro-Spend This Month:** %s%,.2f (%s%% of total)
            • **Top Leak Category:** 🏷️ %s
            • **Projected Annual Drain:** %s%,.2f/year

            💡 **Optimization Strategy:**
            %s
            """,
                curr,
                leaks.get("micro_spend_count"),
                curr, leaks.get("total_micro_amount"), leaks.get("leak_pct"),
                leaks.get("top_leak_category"),
                curr, leaks.get("annual_projected"),
                leaks.get("recommendation")
        );
    }

    private String parseSafeDailyIntent(String text, Long userId, String curr) {
        String lowered = text.toLowerCase();
        List<String> triggers = List.of("safe daily", "spend today", "daily limit", "daily allowance", "how much can i spend");
        if (triggers.stream().noneMatch(lowered::contains)) {
            return null;
        }

        Map<String, Object> s = financialService.calculateSafeDailySpend(userId);
        return String.format("""
            ### 💰 Today's Safe-to-Spend Allowance: **%s%,.2f / day**
            • **Billing Cycle Remaining:** %s days
            • **Discretionary Liquid Buffer:** %s%,.2f
            • **Committed Upcoming Bills:** %s%,.2f

            🛡️ *Spending at or below this run-rate ensures you comfortably meet upcoming recurring bills.*
            """,
                curr, s.get("safe_daily_spend"),
                s.get("days_left"),
                curr, s.get("safe_pool"),
                curr, s.get("upcoming_bills")
        );
    }

    private String parseCashflowCollisionIntent(String text, Long userId, String curr) {
        String lowered = text.toLowerCase();
        List<String> triggers = List.of("collision", "upcoming bill", "bill risk", "shortfall", "upcoming expense");
        if (triggers.stream().noneMatch(lowered::contains)) {
            return null;
        }

        List<Map<String, Object>> alerts = financialService.detectCashflowCollisions(userId);
        if (alerts.isEmpty()) {
            return "✅ **No Cash-Flow Collisions Detected!** Your liquid buffer is projected to comfortably cover all upcoming scheduled bills.";
        }

        Map<String, Object> a = alerts.get(0);
        return String.format("""
            ### 🚨 Cash-Flow Collision Warning on **%s**
            • **Scheduled Bill:** %s (%s%,.2f)
            • **Projected Shortfall:** **%s%,.2f**
            • **Days Remaining:** %s days

            💡 *Recommendation:* Implement a discretionary spending freeze until this bill is settled.
            """,
                a.get("day_name"),
                a.get("bill_name"), curr, a.get("bill_amount"),
                curr, a.get("shortfall"),
                a.get("days_remaining")
        );
    }

    private String parseWhatIfIntent(String text, Long userId, String curr) {
        String lowered = text.toLowerCase();
        if (!lowered.startsWith("what if")) {
            return null;
        }

        Pattern amtPat = Pattern.compile("(?:₹|rs\\.?|\\$)?\\s*(\\d+(?:,\\d{3})*)");
        Matcher amtMatch = amtPat.matcher(lowered);
        double amount = amtMatch.find() ? Double.parseDouble(amtMatch.group(1).replace(",", "")) : 50000.0;

        Map<String, Object> res;
        if (lowered.contains("save") || lowered.contains("saving") || lowered.contains("daily")) {
            double daily = amount < 1000.0 ? amount : 100.0;
            res = financialService.simulateWhatIf(userId, "habit_change", Map.of("daily_savings", daily));
        } else if (lowered.contains("rent") || lowered.contains("cost") || lowered.contains("increase")) {
            res = financialService.simulateWhatIf(userId, "fixed_cost", Map.of("cost_increase", amount));
        } else {
            res = financialService.simulateWhatIf(userId, "major_purchase", Map.of("amount", amount, "item_name", "Major Purchase"));
        }

        return String.format("""
            ### 🧪 Simulation Result: %s
            • **Savings Impact:** %s
            • **Monthly Shift:** %s

            📊 *View full 6-month comparative trajectory in the **Decision Studio** tab.*
            """,
                res.get("scenario_title"),
                res.get("savings_delay"),
                res.get("monthly_impact")
        );
    }

    private String parseAddIntent(String text, Long userId, String curr) {
        Pattern amtPat = Pattern.compile("(?:₹|rs\\.?|\\$)?\\s*(\\d+(?:\\.\\d{1,2})?)");
        Matcher amtMatch = amtPat.matcher(text);
        if (!amtMatch.find()) return null;

        String lowered = text.toLowerCase();
        List<String> triggers = List.of("spent", "paid", "bought", "add expense", "log");
        if (triggers.stream().noneMatch(lowered::contains)) return null;

        double amount = Double.parseDouble(amtMatch.group(1));
        String desc = text.replaceAll("(?i)(?:spent|paid|bought|add expense|log|for|on|₹|rs|\\d+(?:\\.\\d{1,2})?)", "").trim();
        if (desc.isEmpty()) desc = "Expense";

        String category = categorizerService.categorize(desc);
        Long expId = repository.addExpense(userId, LocalDate.now().toString(), amount, desc, category, "UPI", "", "");

        return String.format("✅ **Logged Expense #%d:** %s%,.2f under **%s** (*%s*).", expId, curr, amount, category, desc);
    }

    private String parseBudgetIntent(String text, Long userId, String curr) {
        String lowered = text.toLowerCase();
        List<String> triggers = List.of("budget", "how much spent", "monthly limit");
        if (triggers.stream().noneMatch(lowered::contains)) return null;

        Map<String, Object> summary = repository.getSummary("month", userId);
        User user = repository.getUserById(userId);
        double limit = user != null && user.getMonthlyBudget() != null ? user.getMonthlyBudget() : 25000.0;
        double total = ((Number) summary.get("total")).doubleValue();
        double remaining = Math.max(0.0, limit - total);
        double utilPct = limit > 0 ? (total / limit * 100.0) : 0.0;

        return String.format("""
            ### 🎯 Monthly Budget Status
            • **Total Spent:** %s%,.2f
            • **Monthly Budget Limit:** %s%,.2f
            • **Remaining Safe Pool:** %s%,.2f (%.1f%% utilized)
            """,
                curr, total,
                curr, limit,
                curr, remaining, utilPct
        );
    }

    private String parsePredictIntent(String text, Long userId, String curr) {
        String lowered = text.toLowerCase();
        List<String> triggers = List.of("forecast", "predict", "next month", "prediction");
        if (triggers.stream().noneMatch(lowered::contains)) return null;

        Map<String, Object> forecast = predictorService.predictNextMonth(6, userId);
        return String.format("""
            🔮 **AI Forecast for %s**
            • **Projected Total:** %s%,.2f
            • **Trend:** %s (%+.1f%%)
            • **Confidence Score:** %s%%
            """,
                forecast.get("predicted_month_label"),
                curr, forecast.get("total_next_month"),
                forecast.get("trend"), ((Number) forecast.get("trend_pct")).doubleValue(),
                forecast.get("confidence_score")
        );
    }

    private String generateGeminiResponse(String prompt, Long userId, String currency) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return null;
        }

        try {
            User user = repository.getUserById(userId);
            String userName = user != null && user.getName() != null ? user.getName() : "User";
            double income = user != null && user.getIncome() != null ? user.getIncome() : 50000.0;
            double budget = user != null && user.getMonthlyBudget() != null ? user.getMonthlyBudget() : 25000.0;
            double savingsGoal = user != null && user.getSavingsGoal() != null ? user.getSavingsGoal() : 100000.0;

            Map<String, Object> summary = repository.getSummary("month", userId);
            Map<String, Object> health = financialService.calculateHealthScore(userId);
            Map<String, Object> safeSpend = financialService.calculateSafeDailySpend(userId);
            Map<String, Object> leaks = financialService.detectMoneyLeaks(userId, 300.0);

            String systemContext = String.format("""
                You are 'ExpenseAI Copilot', an empathetic, sharp, and highly intelligent AI personal financial advisor.
                Current User Profile:
                - Name: %s
                - Currency: %s
                - Monthly Income: %s%,.2f
                - Monthly Budget Target: %s%,.2f
                - Savings Goal: %s%,.2f
                - Current Month Total Spend: %s%,.2f (Count: %s transactions)
                - AI Financial Health Score: %s/100 (Grade %s, Status: %s)
                - Safe Daily Spending Allowance: %s%,.2f/day (%s days left in month)
                - Identified Micro-Expense Leaks (< %s300): %s transactions totaling %s%,.2f
                - Category Breakdown: %s

                Instructions:
                1. Answer the user's question directly with clear, concise, actionable financial advice.
                2. Ground your advice using their real financial numbers when relevant.
                3. Keep answers punchy (2-4 concise paragraphs or bullet points). Use Markdown formatting with relevant emojis.
                """,
                    userName, currency,
                    currency, income,
                    currency, budget,
                    currency, savingsGoal,
                    currency, summary.get("total"), summary.get("count"),
                    health.get("score"), health.get("grade"), health.get("status"),
                    currency, safeSpend.get("safe_daily_spend"), safeSpend.get("days_left"),
                    currency, leaks.get("micro_spend_count"), currency, leaks.get("total_micro_amount"),
                    summary.get("by_category")
            );

            Map<String, Object> bodyMap = Map.of(
                    "contents", List.of(
                            Map.of("role", "user", "parts", List.of(
                                    Map.of("text", systemContext + "\n\nUser Question: " + prompt)
                            ))
                    )
            );

            String requestBody = objectMapper.writeValueAsString(bodyMap);
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode candidate = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
                if (!candidate.isMissingNode()) {
                    return candidate.asText().trim();
                }
            }
        } catch (Exception e) {
            System.err.println("Gemini API call notice: " + e.getMessage());
        }
        return null;
    }
}
