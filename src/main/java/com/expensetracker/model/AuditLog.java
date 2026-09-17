package com.expensetracker.model;

public class AuditLog {
    private Long id;
    private Long userId;
    private String userEmail;
    private String action;
    private String ipAddress;
    private String createdAt;

    public AuditLog() {}

    public AuditLog(Long id, Long userId, String userEmail, String action, String ipAddress, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.userEmail = userEmail;
        this.action = action;
        this.ipAddress = ipAddress != null ? ipAddress : "127.0.0.1";
        this.createdAt = createdAt;
    }

    public Object get(String key) {
        if (key == null) return null;
        return switch (key) {
            case "id" -> id;
            case "user_id" -> userId;
            case "user_email" -> userEmail;
            case "action" -> action;
            case "ip_address" -> ipAddress;
            case "created_at" -> createdAt;
            default -> null;
        };
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
