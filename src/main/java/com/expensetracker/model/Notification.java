package com.expensetracker.model;

public class Notification {
    private Long id;
    private Long userId;
    private String title;
    private String message;
    private String type;
    private Integer isRead;
    private String createdAt;

    public Notification() {}

    public Notification(Long id, Long userId, String title, String message, String type, Integer isRead, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.type = type != null ? type : "info";
        this.isRead = isRead != null ? isRead : 0;
        this.createdAt = createdAt;
    }

    public Object get(String key) {
        if (key == null) return null;
        return switch (key) {
            case "id" -> id;
            case "user_id" -> userId;
            case "title" -> title;
            case "message" -> message;
            case "type" -> type;
            case "is_read" -> isRead;
            case "created_at" -> createdAt;
            default -> null;
        };
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getIsRead() { return isRead; }
    public void setIsRead(Integer isRead) { this.isRead = isRead; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
