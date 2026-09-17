package com.expensetracker.repository;

import com.expensetracker.model.*;
import com.expensetracker.service.PasswordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.io.PrintWriter;
import java.io.Writer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Repository
public class ExpenseRepository {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordService passwordService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExpenseRepository(JdbcTemplate jdbcTemplate, PasswordService passwordService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordService = passwordService;
    }

    @PostConstruct
    public void init() {
        ensureDatabaseSchema();
        seedDefaultUsersAndData();
    }

    public void ensureDatabaseSchema() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                role TEXT DEFAULT 'user',
                currency TEXT DEFAULT '₹',
                monthly_budget REAL DEFAULT 25000.0,
                income REAL DEFAULT 50000.0,
                savings_goal REAL DEFAULT 100000.0,
                is_student_mode INTEGER DEFAULT 0,
                monthly_allowance REAL DEFAULT 15000.0,
                theme TEXT DEFAULT 'light',
                created_at TEXT NOT NULL
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL DEFAULT 1,
                date TEXT NOT NULL,
                amount REAL NOT NULL,
                description TEXT NOT NULL,
                category TEXT NOT NULL,
                payment_method TEXT DEFAULT 'UPI',
                notes TEXT DEFAULT '',
                receipt_path TEXT DEFAULT '',
                created_at TEXT NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS budgets (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                category TEXT NOT NULL,
                limit_amount REAL NOT NULL,
                month TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS recurring (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                description TEXT NOT NULL,
                amount REAL NOT NULL,
                category TEXT NOT NULL,
                frequency TEXT NOT NULL,
                next_due_date TEXT NOT NULL,
                is_active INTEGER DEFAULT 1,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS financial_goals (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                target_amount REAL NOT NULL,
                current_amount REAL DEFAULT 0.0,
                target_date TEXT,
                category TEXT DEFAULT 'General',
                created_at TEXT NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                message TEXT NOT NULL,
                type TEXT DEFAULT 'info',
                is_read INTEGER DEFAULT 0,
                created_at TEXT NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS contact_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT NOT NULL,
                subject TEXT NOT NULL,
                message TEXT NOT NULL,
                status TEXT DEFAULT 'unread',
                created_at TEXT NOT NULL
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS audit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                user_email TEXT,
                action TEXT NOT NULL,
                ip_address TEXT DEFAULT '127.0.0.1',
                created_at TEXT NOT NULL
            );
        """);
    }

    public void seedDefaultUsersAndData() {
        String nowIso = LocalDateTime.now().toString();

        // Seed Admin User
        List<User> admins = jdbcTemplate.query(
                "SELECT * FROM users WHERE LOWER(email) = ?",
                userRowMapper,
                "admin@expensetracker.ai"
        );
        if (admins.isEmpty()) {
            jdbcTemplate.update("""
                INSERT INTO users (name, email, password_hash, role, currency, monthly_budget, income, savings_goal, is_student_mode, monthly_allowance, theme, created_at)
                VALUES (?, ?, ?, 'admin', '₹', 50000.0, 95000.0, 250000.0, 0, 15000.0, 'light', ?)
            """, "Admin User", "admin@expensetracker.ai", passwordService.hashPassword("admin123"), nowIso);
        }

        // Seed Demo / Guest User
        List<User> guests = jdbcTemplate.query(
                "SELECT * FROM users WHERE LOWER(email) = ?",
                userRowMapper,
                "guest@expensetracker.ai"
        );
        long guestId;
        if (guests.isEmpty()) {
            jdbcTemplate.update("""
                INSERT INTO users (name, email, password_hash, role, currency, monthly_budget, income, savings_goal, is_student_mode, monthly_allowance, theme, created_at)
                VALUES (?, ?, ?, 'user', '₹', 30000.0, 65000.0, 150000.0, 0, 15000.0, 'light', ?)
            """, "Priya Sharma (Demo)", "guest@expensetracker.ai", passwordService.hashPassword("guest123"), nowIso);

            guestId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        } else {
            guestId = guests.get(0).getId();
        }

        // Map orphaned expenses to guestId
        jdbcTemplate.update("UPDATE expenses SET user_id = ? WHERE user_id IS NULL OR user_id = 0", guestId);

        // Seed default budgets if empty
        Integer budgetCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM budgets WHERE user_id = ?",
                Integer.class,
                guestId
        );
        String currentMonth = YearMonth.now().toString();
        if (budgetCount == null || budgetCount == 0) {
            Object[][] sampleBudgets = {
                    {"Food", 6000.0},
                    {"Travel", 4000.0},
                    {"Bills", 8000.0},
                    {"Entertainment", 3500.0},
                    {"Shopping", 5000.0},
                    {"Health", 3000.0}
            };
            for (Object[] b : sampleBudgets) {
                jdbcTemplate.update("""
                    INSERT INTO budgets (user_id, category, limit_amount, month, created_at)
                    VALUES (?, ?, ?, ?, ?)
                """, guestId, b[0], b[1], currentMonth, nowIso);
            }
        }

        // Seed sample recurring bills if empty
        Integer recCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recurring WHERE user_id = ?",
                Integer.class,
                guestId
        );
        if (recCount == null || recCount == 0) {
            LocalDate today = LocalDate.now();
            int y = today.getYear();
            int m = today.getMonthValue();
            String b1 = String.format("%04d-%02d-15", y, m);
            String b2 = String.format("%04d-%02d-18", y, m);
            String b3 = String.format("%04d-%02d-22", y, m);
            String b4 = String.format("%04d-%02d-25", y, m);

            Object[][] recurringSamples = {
                    {"Apartment Rent", 12000.0, "Bills", "Monthly", b1},
                    {"Personal Loan EMI", 4500.0, "Bills", "Monthly", b2},
                    {"Fiber Internet Broadband", 999.0, "Bills", "Monthly", b3},
                    {"Netflix 4K Subscription", 649.0, "Entertainment", "Monthly", b4}
            };
            for (Object[] r : recurringSamples) {
                jdbcTemplate.update("""
                    INSERT INTO recurring (user_id, description, amount, category, frequency, next_due_date, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, 1)
                """, guestId, r[0], r[1], r[2], r[3], r[4]);
            }
        }

        // Seed notifications if empty
        Integer notifCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ?",
                Integer.class,
                guestId
        );
        if (notifCount == null || notifCount == 0) {
            jdbcTemplate.update("""
                INSERT INTO notifications (user_id, title, message, type, is_read, created_at)
                VALUES (?, '🎉 Welcome to ExpenseAI!', 'Your workspace is active. Explore smart financial intelligence, budgets, and AI forecasts.', 'success', 0, ?)
            """, guestId, nowIso);
        }
    }

    // ==================== USERS & AUTH ====================

    private final RowMapper<User> userRowMapper = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("role"),
            rs.getString("currency"),
            rs.getDouble("monthly_budget"),
            rs.getDouble("income"),
            rs.getDouble("savings_goal"),
            rs.getInt("is_student_mode"),
            rs.getDouble("monthly_allowance"),
            rs.getString("theme"),
            rs.getString("created_at")
    );

    public User getUserById(Long id) {
        if (id == null) return null;
        List<User> list = jdbcTemplate.query("SELECT * FROM users WHERE id = ?", userRowMapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public User getUserByEmail(String email) {
        if (email == null) return null;
        List<User> list = jdbcTemplate.query("SELECT * FROM users WHERE LOWER(email) = ?", userRowMapper, email.trim().toLowerCase());
        return list.isEmpty() ? null : list.get(0);
    }

    public User authenticateUser(String email, String rawPassword) {
        User user = getUserByEmail(email);
        if (user != null && passwordService.verifyPassword(rawPassword, user.getPasswordHash())) {
            return user;
        }
        return null;
    }

    public Long createUser(String name, String email, String rawPassword, String currency) {
        String hash = passwordService.hashPassword(rawPassword);
        String nowIso = LocalDateTime.now().toString();
        jdbcTemplate.update("""
            INSERT INTO users (name, email, password_hash, role, currency, monthly_budget, income, savings_goal, is_student_mode, monthly_allowance, theme, created_at)
            VALUES (?, ?, ?, 'user', ?, 25000.0, 50000.0, 100000.0, 0, 15000.0, 'light', ?)
        """, name, email.trim().toLowerCase(), hash, currency != null ? currency : "₹", nowIso);
        return jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    public void updateUserProfile(Long userId, String name, String currency, Double monthlyBudget,
                                  Double income, Double savingsGoal, Integer isStudentMode,
                                  Double monthlyAllowance, String theme) {
        jdbcTemplate.update("""
            UPDATE users
            SET name = ?, currency = ?, monthly_budget = ?, income = ?, savings_goal = ?,
                is_student_mode = ?, monthly_allowance = ?, theme = ?
            WHERE id = ?
        """, name, currency, monthlyBudget, income, savingsGoal, isStudentMode, monthlyAllowance, theme, userId);
    }

    public void updateUserPassword(Long userId, String newPassword) {
        String hash = passwordService.hashPassword(newPassword);
        jdbcTemplate.update("UPDATE users SET password_hash = ? WHERE id = ?", hash, userId);
    }

    public void deleteUser(Long userId) {
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    // ==================== EXPENSES ====================

    private final RowMapper<Expense> expenseRowMapper = (rs, rowNum) -> new Expense(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("date"),
            rs.getDouble("amount"),
            rs.getString("description"),
            rs.getString("category"),
            rs.getString("payment_method"),
            rs.getString("notes"),
            rs.getString("receipt_path"),
            rs.getString("created_at")
    );

    public Long addExpense(Long userId, String dateIso, Double amount, String description,
                           String category, String paymentMethod, String notes, String receiptPath) {
        String nowIso = LocalDateTime.now().toString();
        jdbcTemplate.update("""
            INSERT INTO expenses (user_id, date, amount, description, category, payment_method, notes, receipt_path, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, userId, dateIso, amount, description, category,
                paymentMethod != null ? paymentMethod : "UPI",
                notes != null ? notes : "",
                receiptPath != null ? receiptPath : "",
                nowIso);
        return jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    public int countExpenses(Long userId, String startDate, String endDate, String category,
                             String searchQuery, String paymentMethod) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM expenses WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (userId != null) {
            sql.append(" AND user_id = ?");
            params.add(userId);
        }
        if (startDate != null && !startDate.isBlank()) {
            sql.append(" AND date >= ?");
            params.add(startDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            sql.append(" AND date <= ?");
            params.add(endDate);
        }
        if (category != null && !category.isBlank() && !category.equalsIgnoreCase("all")) {
            sql.append(" AND LOWER(category) = ?");
            params.add(category.toLowerCase());
        }
        if (paymentMethod != null && !paymentMethod.isBlank() && !paymentMethod.equalsIgnoreCase("all")) {
            sql.append(" AND LOWER(payment_method) = ?");
            params.add(paymentMethod.toLowerCase());
        }
        if (searchQuery != null && !searchQuery.isBlank()) {
            sql.append(" AND (LOWER(description) LIKE ? OR LOWER(notes) LIKE ?)");
            String q = "%" + searchQuery.toLowerCase() + "%";
            params.add(q);
            params.add(q);
        }

        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    public List<Expense> listExpenses(Long userId, String startDate, String endDate, String category,
                                      String searchQuery, String paymentMethod, String sortBy,
                                      int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM expenses WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (userId != null) {
            sql.append(" AND user_id = ?");
            params.add(userId);
        }
        if (startDate != null && !startDate.isBlank()) {
            sql.append(" AND date >= ?");
            params.add(startDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            sql.append(" AND date <= ?");
            params.add(endDate);
        }
        if (category != null && !category.isBlank() && !category.equalsIgnoreCase("all")) {
            sql.append(" AND LOWER(category) = ?");
            params.add(category.toLowerCase());
        }
        if (paymentMethod != null && !paymentMethod.isBlank() && !paymentMethod.equalsIgnoreCase("all")) {
            sql.append(" AND LOWER(payment_method) = ?");
            params.add(paymentMethod.toLowerCase());
        }
        if (searchQuery != null && !searchQuery.isBlank()) {
            sql.append(" AND (LOWER(description) LIKE ? OR LOWER(notes) LIKE ?)");
            String q = "%" + searchQuery.toLowerCase() + "%";
            params.add(q);
            params.add(q);
        }

        // Sorting
        if ("amount_desc".equalsIgnoreCase(sortBy)) {
            sql.append(" ORDER BY amount DESC, date DESC");
        } else if ("amount_asc".equalsIgnoreCase(sortBy)) {
            sql.append(" ORDER BY amount ASC, date DESC");
        } else if ("date_asc".equalsIgnoreCase(sortBy)) {
            sql.append(" ORDER BY date ASC, id ASC");
        } else {
            sql.append(" ORDER BY date DESC, id DESC");
        }

        if (limit > 0) {
            sql.append(" LIMIT ? OFFSET ?");
            params.add(limit);
            params.add(offset);
        }

        return jdbcTemplate.query(sql.toString(), expenseRowMapper, params.toArray());
    }

    public List<Expense> listRecentExpenses(Long userId, int limit) {
        return listExpenses(userId, null, null, null, null, null, "date_desc", limit, 0);
    }

    public void deleteExpense(Long expenseId, Long userId) {
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM expenses WHERE id = ? AND user_id = ?", expenseId, userId);
        } else {
            jdbcTemplate.update("DELETE FROM expenses WHERE id = ?", expenseId);
        }
    }

    // ==================== AGGREGATIONS & SUMMARY ====================

    public Map<String, Object> getSummary(String period, Long userId) {
        LocalDate today = LocalDate.now();
        String startDate = null;
        String endDate = today.toString();

        if ("today".equalsIgnoreCase(period)) {
            startDate = today.toString();
        } else if ("week".equalsIgnoreCase(period)) {
            startDate = today.minusDays(7).toString();
        } else if ("month".equalsIgnoreCase(period)) {
            startDate = today.withDayOfMonth(1).toString();
        } else if ("year".equalsIgnoreCase(period)) {
            startDate = today.withDayOfYear(1).toString();
        }

        StringBuilder baseSql = new StringBuilder("FROM expenses WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (userId != null) {
            baseSql.append(" AND user_id = ?");
            params.add(userId);
        }
        if (startDate != null) {
            baseSql.append(" AND date >= ?");
            params.add(startDate);
        }
        if (endDate != null) {
            baseSql.append(" AND date <= ?");
            params.add(endDate);
        }

        String statsSql = "SELECT COALESCE(SUM(amount), 0.0) as total, COUNT(*) as count, " +
                "COALESCE(AVG(amount), 0.0) as avg, COALESCE(MIN(amount), 0.0) as min_amt, " +
                "COALESCE(MAX(amount), 0.0) as max_amt " + baseSql;

        Map<String, Object> stats = jdbcTemplate.queryForMap(statsSql, params.toArray());

        double total = ((Number) stats.get("total")).doubleValue();
        int count = ((Number) stats.get("count")).intValue();
        double avg = ((Number) stats.get("avg")).doubleValue();
        double minAmt = ((Number) stats.get("min_amt")).doubleValue();
        double maxAmt = ((Number) stats.get("max_amt")).doubleValue();

        // Calculate daily average
        int days = 1;
        if ("week".equalsIgnoreCase(period)) {
            days = 7;
        } else if ("month".equalsIgnoreCase(period)) {
            days = Math.max(1, today.getDayOfMonth());
        } else if ("year".equalsIgnoreCase(period)) {
            days = Math.max(1, today.getDayOfYear());
        }
        double dailyAvg = total / days;

        // By Category & Counts
        String catSql = "SELECT category, SUM(amount) as s, COUNT(*) as c " + baseSql + " GROUP BY category ORDER BY s DESC";
        Map<String, Double> byCategory = new LinkedHashMap<>();
        Map<String, Integer> byCategoryCounts = new LinkedHashMap<>();
        jdbcTemplate.query(catSql, rs -> {
            String c = rs.getString("category");
            byCategory.put(c, Math.round(rs.getDouble("s") * 100.0) / 100.0);
            byCategoryCounts.put(c, rs.getInt("c"));
        }, params.toArray());

        // By Payment Method
        String paySql = "SELECT payment_method, SUM(amount) as s " + baseSql + " GROUP BY payment_method ORDER BY s DESC";
        Map<String, Double> byPayment = new LinkedHashMap<>();
        jdbcTemplate.query(paySql, rs -> {
            byPayment.put(rs.getString("payment_method"), Math.round(rs.getDouble("s") * 100.0) / 100.0);
        }, params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", Math.round(total * 100.0) / 100.0);
        result.put("count", count);
        result.put("average", Math.round(avg * 100.0) / 100.0);
        result.put("daily_average", Math.round(dailyAvg * 100.0) / 100.0);
        result.put("min", Math.round(minAmt * 100.0) / 100.0);
        result.put("max", Math.round(maxAmt * 100.0) / 100.0);
        result.put("by_category", byCategory);
        result.put("by_category_counts", byCategoryCounts);
        result.put("by_payment", byPayment);
        result.put("period", period != null ? period : "all");

        return result;
    }

    public Map<String, Double> monthlyTotals(Long userId) {
        StringBuilder sql = new StringBuilder("""
            SELECT SUBSTR(date, 1, 7) as ym, SUM(amount) as total
            FROM expenses
            WHERE 1=1
        """);
        List<Object> params = new ArrayList<>();
        if (userId != null) {
            sql.append(" AND user_id = ?");
            params.add(userId);
        }
        sql.append(" GROUP BY ym ORDER BY ym ASC");

        Map<String, Double> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql.toString(), rs -> {
            String ym = rs.getString("ym");
            if (ym != null && ym.length() == 7) {
                result.put(ym, Math.round(rs.getDouble("total") * 100.0) / 100.0);
            }
        }, params.toArray());
        return result;
    }

    public Map<String, Map<String, Double>> monthlyTotalsByCategory(Long userId) {
        StringBuilder sql = new StringBuilder("""
            SELECT SUBSTR(date, 1, 7) as ym, category, SUM(amount) as total
            FROM expenses
            WHERE 1=1
        """);
        List<Object> params = new ArrayList<>();
        if (userId != null) {
            sql.append(" AND user_id = ?");
            params.add(userId);
        }
        sql.append(" GROUP BY ym, category ORDER BY ym ASC");

        Map<String, Map<String, Double>> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql.toString(), rs -> {
            String ym = rs.getString("ym");
            String cat = rs.getString("category");
            double total = Math.round(rs.getDouble("total") * 100.0) / 100.0;
            result.computeIfAbsent(ym, k -> new LinkedHashMap<>()).put(cat, total);
        }, params.toArray());
        return result;
    }

    public Map<String, Double> getDailyTotalsCurrentMonth(Long userId) {
        String currentYm = YearMonth.now().toString();
        StringBuilder sql = new StringBuilder("""
            SELECT date, SUM(amount) as total
            FROM expenses
            WHERE SUBSTR(date, 1, 7) = ?
        """);
        List<Object> params = new ArrayList<>();
        params.add(currentYm);

        if (userId != null) {
            sql.append(" AND user_id = ?");
            params.add(userId);
        }
        sql.append(" GROUP BY date ORDER BY date ASC");

        Map<String, Double> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql.toString(), rs -> {
            result.put(rs.getString("date"), rs.getDouble("total"));
        }, params.toArray());
        return result;
    }

    public Map<String, Double> getWeekdaySpendingDistribution(Long userId) {
        StringBuilder sql = new StringBuilder("SELECT date, amount FROM expenses WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (userId != null) {
            sql.append(" AND user_id = ?");
            params.add(userId);
        }

        Map<String, Double> dist = new LinkedHashMap<>();
        dist.put("Monday", 0.0);
        dist.put("Tuesday", 0.0);
        dist.put("Wednesday", 0.0);
        dist.put("Thursday", 0.0);
        dist.put("Friday", 0.0);
        dist.put("Saturday", 0.0);
        dist.put("Sunday", 0.0);

        jdbcTemplate.query(sql.toString(), rs -> {
            String dateStr = rs.getString("date");
            double amt = rs.getDouble("amount");
            try {
                LocalDate d = LocalDate.parse(dateStr);
                DayOfWeek dow = d.getDayOfWeek();
                String dayName = switch (dow) {
                    case MONDAY -> "Monday";
                    case TUESDAY -> "Tuesday";
                    case WEDNESDAY -> "Wednesday";
                    case THURSDAY -> "Thursday";
                    case FRIDAY -> "Friday";
                    case SATURDAY -> "Saturday";
                    case SUNDAY -> "Sunday";
                };
                dist.put(dayName, dist.get(dayName) + amt);
            } catch (Exception ignored) {}
        }, params.toArray());

        for (Map.Entry<String, Double> e : dist.entrySet()) {
            dist.put(e.getKey(), Math.round(e.getValue() * 100.0) / 100.0);
        }
        return dist;
    }

    // ==================== BUDGETS & RECURRING ====================

    private final RowMapper<Budget> budgetRowMapper = (rs, rowNum) -> new Budget(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("category"),
            rs.getDouble("limit_amount"),
            rs.getString("month"),
            rs.getString("created_at")
    );

    public List<Budget> getUserBudgets(Long userId, String month) {
        String targetMonth = month != null ? month : YearMonth.now().toString();
        List<Budget> budgets = jdbcTemplate.query(
                "SELECT * FROM budgets WHERE user_id = ? AND month = ?",
                budgetRowMapper,
                userId, targetMonth
        );

        // Fetch spending per category for this month
        Map<String, Double> spentMap = new HashMap<>();
        jdbcTemplate.query("""
            SELECT category, SUM(amount) as s
            FROM expenses
            WHERE user_id = ? AND SUBSTR(date, 1, 7) = ?
            GROUP BY category
        """, rs -> {
            spentMap.put(rs.getString("category"), rs.getDouble("s"));
        }, userId, targetMonth);

        for (Budget b : budgets) {
            double spent = spentMap.getOrDefault(b.getCategory(), 0.0);
            b.setSpent(Math.round(spent * 100.0) / 100.0);
            b.setRemaining(Math.max(0.0, Math.round((b.getLimitAmount() - spent) * 100.0) / 100.0));
            double pct = b.getLimitAmount() > 0 ? (spent / b.getLimitAmount()) * 100.0 : 0.0;
            b.setPercentage(Math.round(pct * 10.0) / 10.0);
            b.setIsExceeded(spent > b.getLimitAmount());
        }

        return budgets;
    }

    public void setBudget(Long userId, String category, Double limitAmount, String month) {
        String targetMonth = month != null ? month : YearMonth.now().toString();
        String nowIso = LocalDateTime.now().toString();

        List<Budget> existing = jdbcTemplate.query(
                "SELECT * FROM budgets WHERE user_id = ? AND category = ? AND month = ?",
                budgetRowMapper,
                userId, category, targetMonth
        );

        if (!existing.isEmpty()) {
            jdbcTemplate.update("""
                UPDATE budgets SET limit_amount = ? WHERE id = ?
            """, limitAmount, existing.get(0).getId());
        } else {
            jdbcTemplate.update("""
                INSERT INTO budgets (user_id, category, limit_amount, month, created_at)
                VALUES (?, ?, ?, ?, ?)
            """, userId, category, limitAmount, targetMonth, nowIso);
        }
    }

    public void deleteBudget(Long budgetId, Long userId) {
        jdbcTemplate.update("DELETE FROM budgets WHERE id = ? AND user_id = ?", budgetId, userId);
    }

    private final RowMapper<Recurring> recurringRowMapper = (rs, rowNum) -> new Recurring(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("description"),
            rs.getDouble("amount"),
            rs.getString("category"),
            rs.getString("frequency"),
            rs.getString("next_due_date"),
            rs.getInt("is_active") == 1
    );

    public List<Recurring> listRecurring(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM recurring WHERE user_id = ? ORDER BY next_due_date ASC",
                recurringRowMapper,
                userId
        );
    }

    public void addRecurring(Long userId, String description, Double amount, String category,
                             String frequency, String nextDueDate) {
        jdbcTemplate.update("""
            INSERT INTO recurring (user_id, description, amount, category, frequency, next_due_date, is_active)
            VALUES (?, ?, ?, ?, ?, ?, 1)
        """, userId, description, amount, category, frequency != null ? frequency : "Monthly", nextDueDate);
    }

    public void deleteRecurring(Long recurringId, Long userId) {
        jdbcTemplate.update("DELETE FROM recurring WHERE id = ? AND user_id = ?", recurringId, userId);
    }

    // ==================== NOTIFICATIONS ====================

    private final RowMapper<Notification> notificationRowMapper = (rs, rowNum) -> new Notification(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("title"),
            rs.getString("message"),
            rs.getString("type"),
            rs.getInt("is_read"),
            rs.getString("created_at")
    );

    public List<Notification> getNotifications(Long userId, int limit) {
        return jdbcTemplate.query("""
            SELECT * FROM notifications WHERE user_id = ? ORDER BY id DESC LIMIT ?
        """, notificationRowMapper, userId, limit);
    }

    public int countUnreadNotifications(Long userId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0
        """, Integer.class, userId);
        return count != null ? count : 0;
    }

    public void addNotification(Long userId, String title, String message, String type) {
        String nowIso = LocalDateTime.now().toString();
        jdbcTemplate.update("""
            INSERT INTO notifications (user_id, title, message, type, is_read, created_at)
            VALUES (?, ?, ?, ?, 0, ?)
        """, userId, title, message, type != null ? type : "info", nowIso);
    }

    public void markAllNotificationsRead(Long userId) {
        jdbcTemplate.update("UPDATE notifications SET is_read = 1 WHERE user_id = ?", userId);
    }

    public void clearAllNotifications(Long userId) {
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", userId);
    }

    // ==================== AUDIT LOGS & CONTACT ====================

    public void logAudit(String action, Long userId, String userEmail, String ipAddress) {
        String nowIso = LocalDateTime.now().toString();
        jdbcTemplate.update("""
            INSERT INTO audit_logs (user_id, user_email, action, ip_address, created_at)
            VALUES (?, ?, ?, ?, ?)
        """, userId, userEmail, action, ipAddress != null ? ipAddress : "127.0.0.1", nowIso);
    }

    public void saveContactMessage(String name, String email, String subject, String message) {
        String nowIso = LocalDateTime.now().toString();
        jdbcTemplate.update("""
            INSERT INTO contact_messages (name, email, subject, message, status, created_at)
            VALUES (?, ?, ?, ?, 'unread', ?)
        """, name, email, subject, message, nowIso);
    }

    public Map<String, Object> getAdminMetrics() {
        int totalUsers = Optional.ofNullable(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).orElse(0);
        int totalExpenses = Optional.ofNullable(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM expenses", Integer.class)).orElse(0);
        double totalVolume = Optional.ofNullable(jdbcTemplate.queryForObject("SELECT COALESCE(SUM(amount), 0.0) FROM expenses", Double.class)).orElse(0.0);
        int unreadContacts = Optional.ofNullable(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM contact_messages WHERE status = 'unread'", Integer.class)).orElse(0);

        List<User> recentUsers = jdbcTemplate.query("SELECT * FROM users ORDER BY id DESC LIMIT 10", userRowMapper);
        List<ContactMessage> contactMessages = jdbcTemplate.query("""
            SELECT * FROM contact_messages ORDER BY id DESC LIMIT 10
        """, (rs, rowNum) -> new ContactMessage(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("subject"),
                rs.getString("message"),
                rs.getString("status"),
                rs.getString("created_at")
        ));

        List<AuditLog> recentLogs = jdbcTemplate.query("""
            SELECT * FROM audit_logs ORDER BY id DESC LIMIT 15
        """, (rs, rowNum) -> new AuditLog(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("user_email"),
                rs.getString("action"),
                rs.getString("ip_address"),
                rs.getString("created_at")
        ));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("total_users", totalUsers);
        metrics.put("total_expenses", totalExpenses);
        metrics.put("total_volume", Math.round(totalVolume * 100.0) / 100.0);
        metrics.put("unread_contacts", unreadContacts);
        metrics.put("recent_users", recentUsers);
        metrics.put("contact_messages", contactMessages);
        metrics.put("recent_logs", recentLogs);

        return metrics;
    }

    // ==================== CSV & JSON EXPORT ====================

    public void exportCsv(Long userId, Writer writer) {
        PrintWriter pw = new PrintWriter(writer);
        pw.println("id,date,amount,description,category,payment_method,notes,created_at");

        List<Expense> list = listExpenses(userId, null, null, null, null, null, "date_desc", -1, 0);
        for (Expense e : list) {
            pw.printf("%d,%s,%.2f,\"%s\",\"%s\",\"%s\",\"%s\",%s%n",
                    e.getId(),
                    e.getDate(),
                    e.getAmount(),
                    e.getDescription().replace("\"", "\"\""),
                    e.getCategory().replace("\"", "\"\""),
                    e.getPaymentMethod().replace("\"", "\"\""),
                    e.getNotes().replace("\"", "\"\""),
                    e.getCreatedAt()
            );
        }
        pw.flush();
    }

    public String exportJson(Long userId) {
        List<Expense> list = listExpenses(userId, null, null, null, null, null, "date_desc", -1, 0);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
