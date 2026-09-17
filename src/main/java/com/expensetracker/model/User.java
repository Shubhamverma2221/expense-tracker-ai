package com.expensetracker.model;

import java.time.LocalDateTime;

public class User {
    private Long id;
    private String name;
    private String email;
    private String passwordHash;
    private String role;
    private String currency;
    private Double monthlyBudget;
    private Double income;
    private Double savingsGoal;
    private Integer isStudentMode;
    private Double monthlyAllowance;
    private String theme;
    private String createdAt;

    public User() {
    }

    public User(Long id, String name, String email, String passwordHash, String role, String currency,
                Double monthlyBudget, Double income, Double savingsGoal, Integer isStudentMode,
                Double monthlyAllowance, String theme, String createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role != null ? role : "user";
        this.currency = currency != null ? currency : "₹";
        this.monthlyBudget = monthlyBudget != null ? monthlyBudget : 25000.0;
        this.income = income != null ? income : 50000.0;
        this.savingsGoal = savingsGoal != null ? savingsGoal : 100000.0;
        this.isStudentMode = isStudentMode != null ? isStudentMode : 0;
        this.monthlyAllowance = monthlyAllowance != null ? monthlyAllowance : 15000.0;
        this.theme = theme != null ? theme : "light";
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now().toString();
    }

    // Helper for template calls like user.get('key')
    public Object get(String key) {
        if (key == null) return null;
        return switch (key) {
            case "id" -> id;
            case "name" -> name;
            case "email" -> email;
            case "role" -> role;
            case "currency" -> currency;
            case "monthly_budget" -> monthlyBudget;
            case "income" -> income;
            case "savings_goal" -> savingsGoal;
            case "is_student_mode" -> isStudentMode;
            case "monthly_allowance" -> monthlyAllowance;
            case "theme" -> theme;
            case "created_at" -> createdAt;
            default -> null;
        };
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Double getMonthlyBudget() { return monthlyBudget; }
    public void setMonthlyBudget(Double monthlyBudget) { this.monthlyBudget = monthlyBudget; }

    public Double getIncome() { return income; }
    public void setIncome(Double income) { this.income = income; }

    public Double getSavingsGoal() { return savingsGoal; }
    public void setSavingsGoal(Double savingsGoal) { this.savingsGoal = savingsGoal; }

    public Integer getIsStudentMode() { return isStudentMode; }
    public void setIsStudentMode(Integer isStudentMode) { this.isStudentMode = isStudentMode; }

    public Double getMonthlyAllowance() { return monthlyAllowance; }
    public void setMonthlyAllowance(Double monthlyAllowance) { this.monthlyAllowance = monthlyAllowance; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
