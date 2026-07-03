package com.bank.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum EventType {
        LOGIN, LOGOUT, LOGIN_FAILED, ACCOUNT_LOCKED,
        PASSWORD_CHANGED, USER_CREATED, USER_DEACTIVATED,
        UNAUTHORIZED_ACCESS, SESSION_EXPIRED
    }

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public AuditLog() {}

    public AuditLog(String username, EventType eventType, String description, boolean success) {
        this.username = username;
        this.eventType = eventType;
        this.description = description;
        this.success = success;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public EventType getEventType() { return eventType; }
    public String getDescription() { return description; }
    public boolean isSuccess() { return success; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
