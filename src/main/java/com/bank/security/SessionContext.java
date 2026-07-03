package com.bank.security;

import com.bank.model.Account;
import com.bank.model.User;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class SessionContext {

    private String token;
    private String username;
    private User.Role role;
    private LocalDateTime loginTime;

    // For customer  all their accounts + the currently active one enables multiple active accounts under one user


    private List<Account> accounts;
    private String activeAccountNumber;

    private final JwtUtil jwtUtil;

    public SessionContext(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public void store(String token, User user) {
        var claims = jwtUtil.validateAndExtract(token);
        this.token    = token;
        this.username = claims.getSubject();
        this.role     = User.Role.valueOf(claims.get("role", String.class));
        this.loginTime = LocalDateTime.now();

        if (user.getRole() == User.Role.CUSTOMER && !user.getAccounts().isEmpty()) {
            this.accounts = user.getAccounts();
            this.activeAccountNumber = user.getAccounts().get(0).getAccountNumber();
        } else {
            this.accounts = null;
            this.activeAccountNumber = null;
        }
    }

    public void clear() {
        this.token = null;
        this.username = null;
        this.role = null;
        this.loginTime = null;
        this.accounts = null;
        this.activeAccountNumber = null;
    }

    public boolean isLoggedIn() {
        return token != null && jwtUtil.isValid(token) && !jwtUtil.isExpired(token);
    }

    public void requireAuth() {
        if (token == null) throw new SecurityException("Not logged in. Please authenticate first.");
        if (!jwtUtil.isValid(token)) throw new SecurityException("Invalid session token.");
        if (jwtUtil.isExpired(token)) {
            clear();
            throw new SecurityException("Session expired. Please log in again.");
        }
    }

    public void requireRole(User.Role... allowedRoles) {
        requireAuth();
        for (User.Role allowed : allowedRoles) {
            if (this.role == allowed) return;
        }
        throw new SecurityException(
            "Access denied. Required role: " + java.util.Arrays.toString(allowedRoles) +
            ", your role: " + this.role
        );
    }


    public void requireAccountAccess(String requestedAccountNumber) {
        requireAuth();
        if (role == User.Role.CUSTOMER) {
            boolean owns = accounts != null && accounts.stream()
                    .anyMatch(a -> a.getAccountNumber().equals(requestedAccountNumber));
            if (!owns) {
                throw new SecurityException(
                    "Access denied. Account " + requestedAccountNumber + " does not belong to you."
                );
            }
        }
    }

    public void switchAccount(String accountNumber) {
        requireRole(User.Role.CUSTOMER);
        requireAccountAccess(accountNumber);
        this.activeAccountNumber = accountNumber;
    }

    public String getSessionInfo() {
        if (!isLoggedIn()) return "No active session";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        long minutesLeft = jwtUtil.getExpirationMs() / 60000;
        String info = String.format("User: %s | Role: %s | Logged in: %s | Session expires in ~%d min",
                username, role, loginTime.format(fmt), minutesLeft);
        if (activeAccountNumber != null) {
            info += "\n  Active Account: " + activeAccountNumber;
            if (accounts != null) {
                info += " (You have " + accounts.size() + " account(s))";
            }
        }
        return info;
    }

    public String getToken()               { return token; }
    public String getUsername()            { return username; }
    public User.Role getRole()             { return role; }
    public String getActiveAccountNumber() { return activeAccountNumber; }
    public List<Account> getAccounts()     { return accounts; }
    public LocalDateTime getLoginTime()    { return loginTime; }

    public String getAccountNumber()       { return activeAccountNumber; }
}
