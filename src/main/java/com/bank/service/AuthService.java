package com.bank.service;

import com.bank.exception.BankException;
import com.bank.model.Account;
import com.bank.model.AuditLog;
import com.bank.model.User;
import com.bank.repository.AuditLogRepository;
import com.bank.repository.UserRepository;
import com.bank.security.JwtUtil;
import com.bank.security.SessionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final AuditLogRepository auditRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SessionContext session;

    @Value("${security.max-login-attempts}")
    private int maxAttempts;

    @Value("${security.lockout-minutes}")
    private int lockoutMinutes;

    public AuthService(UserRepository userRepo, AuditLogRepository auditRepo,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       SessionContext session) {
        this.userRepo        = userRepo;
        this.auditRepo       = auditRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil         = jwtUtil;
        this.session         = session;
    }


    @Transactional
    public String login(String username, String rawPassword) {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> {
                    audit(username, AuditLog.EventType.LOGIN_FAILED, "Username not found", false);
                    return new BankException("Invalid username or password.");
                });

        if (!user.isActive()) {
            audit(username, AuditLog.EventType.LOGIN_FAILED, "Account deactivated", false);
            throw new BankException("This user account has been deactivated.");
        }

        if (user.isLocked()) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
            audit(username, AuditLog.EventType.ACCOUNT_LOCKED, "Login attempt while locked", false);
            throw new BankException(
                "Account locked until " + user.getLockedUntil().format(fmt) +
                " due to too many failed attempts."
            );
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            user.incrementFailedAttempts(maxAttempts, lockoutMinutes);
            userRepo.save(user);

            int remaining = maxAttempts - user.getFailedAttempts();
            audit(username, AuditLog.EventType.LOGIN_FAILED,
                    "Wrong password, attempts=" + user.getFailedAttempts(), false);

            if (user.isLocked()) {
                audit(username, AuditLog.EventType.ACCOUNT_LOCKED,
                        "Locked for " + lockoutMinutes + " minutes", false);
                throw new BankException("Too many failed attempts. Account locked for " +
                        lockoutMinutes + " minutes.");
            }
            throw new BankException("Invalid username or password. " +
                    (remaining > 0 ? remaining + " attempt(s) remaining." : ""));
        }

        user.resetFailedAttempts();
        user.setLastLogin(LocalDateTime.now());
        userRepo.save(user);

        String token = jwtUtil.generateToken(user);
        session.store(token, user);

        audit(username, AuditLog.EventType.LOGIN, "Successful login, role=" + user.getRole(), true);
        return token;
    }

    @Transactional
    public void logout() {
        session.requireAuth();
        audit(session.getUsername(), AuditLog.EventType.LOGOUT, "User logged out", true);
        session.clear();
    }


    @Transactional
    public User registerAdmin(String username, String rawPassword) {
        validatePasswordStrength(rawPassword);
        if (userRepo.existsByUsername(username)) {
            throw new BankException("Username already taken: " + username);
        }
        String hash = passwordEncoder.encode(rawPassword);
        User user = new User(username, hash, User.Role.ADMIN);
        user.getPasswordHistory().add(hash);
        User saved = userRepo.save(user);
        audit("SYSTEM", AuditLog.EventType.USER_CREATED, "Admin created: " + username, true);
        return saved;
    }

    @Transactional
    public User registerTeller(String username, String rawPassword) {
        session.requireRole(User.Role.ADMIN);
        validatePasswordStrength(rawPassword);
        if (userRepo.existsByUsername(username)) {
            throw new BankException("Username already taken: " + username);
        }
        String hash = passwordEncoder.encode(rawPassword);
        User user = new User(username, hash, User.Role.TELLER);
        user.getPasswordHistory().add(hash);
        User saved = userRepo.save(user);
        audit(session.getUsername(), AuditLog.EventType.USER_CREATED, "Teller created: " + username, true);
        return saved;
    }


    @Transactional
    public User registerCustomer(String username, String rawPassword, Account account) {
        session.requireRole(User.Role.ADMIN, User.Role.TELLER);
        validatePasswordStrength(rawPassword);
        if (userRepo.existsByUsername(username)) {
            throw new BankException("Username already taken: " + username);
        }
        String hash = passwordEncoder.encode(rawPassword);
        User user = new User(username, hash, User.Role.CUSTOMER);
        user.getAccounts().add(account);
        user.getPasswordHistory().add(hash);
        User saved = userRepo.save(user);
        audit(session.getUsername(), AuditLog.EventType.USER_CREATED,
                "Customer created: " + username + " with account " + account.getAccountNumber(), true);
        return saved;
    }


    @Transactional
    public void linkAccountToUser(String username, Account account) {
        session.requireRole(User.Role.ADMIN, User.Role.TELLER);

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BankException("User not found: " + username));

        if (user.getRole() != User.Role.CUSTOMER) {
            throw new BankException("Only CUSTOMER users can be linked to accounts.");
        }



        if (user.ownsAccount(account.getAccountNumber())) {
            throw new BankException("Account " + account.getAccountNumber() +
                                    " is already linked to user: " + username);
        }

        userRepo.findCustomerByAccount(account).ifPresent(existing -> {
            if (!existing.getUsername().equals(username)) {
                throw new BankException("Account " + account.getAccountNumber() +
                                        " is already linked to another user: " + existing.getUsername());
            }
        });

        user.getAccounts().add(account);
        userRepo.save(user);
        audit(session.getUsername(), AuditLog.EventType.USER_CREATED,
                "Account " + account.getAccountNumber() + " linked to user: " + username, true);
    }

    @Transactional
    public void changePassword(String username, String oldPassword, String newPassword) {
        session.requireAuth();
        if (!session.getUsername().equals(username)) {
            session.requireRole(User.Role.ADMIN);
        }

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BankException("User not found: " + username));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BankException("Current password is incorrect.");
        }

        validatePasswordStrength(newPassword);

        for (String oldHash : user.getPasswordHistory()) {
            if (passwordEncoder.matches(newPassword, oldHash)) {
                throw new BankException("New password was used recently. Please choose a different password.");
            }
        }

        String newHash = passwordEncoder.encode(newPassword);
        user.setPasswordHash(newHash);

        List<String> history = user.getPasswordHistory();
        history.add(newHash);
        if (history.size() > 3) history.remove(0);

        userRepo.save(user);
        audit(username, AuditLog.EventType.PASSWORD_CHANGED, "Password changed", true);
    }

    @Transactional
    public void deactivateUser(String username) {
        session.requireRole(User.Role.ADMIN);
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BankException("User not found: " + username));
        user.setActive(false);
        userRepo.save(user);
        audit(session.getUsername(), AuditLog.EventType.USER_DEACTIVATED, "Deactivated: " + username, true);
    }

    @Transactional
    public void unlockUser(String username) {
        session.requireRole(User.Role.ADMIN);
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BankException("User not found: " + username));
        user.resetFailedAttempts();
        userRepo.save(user);
    }

                 public List<User> listAllUsers() {
        session.requireRole(User.Role.ADMIN);
        return userRepo.findAll();
    }

    public List<AuditLog> getAuditLogs(int limit) {
        session.requireRole(User.Role.ADMIN);
        return auditRepo.findRecent(org.springframework.data.domain.PageRequest.of(0, limit));
    }

    public List<AuditLog> getUserAuditLogs(String username) {
        session.requireRole(User.Role.ADMIN);
        return auditRepo.findByUsernameOrderByCreatedAtDesc(username);
    }

    public void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8)
            throw new BankException("Password must be at least 8 characters long.");


        if (!password.matches(".*[A-Z].*"))
            throw new BankException("Password must contain at least one uppercase letter.");


                                if (!password.matches(".*[0-9].*"))
            throw new BankException("Password must contain at least one digit.");
            if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\\\"\\\\|,.<>/?].*"))
            throw new BankException("Password must contain at least one special character.");
    }

    private void audit(String username, AuditLog.EventType type, String description, boolean success) {
        try {
            auditRepo.save(new AuditLog(username, type, description, success));
        } catch (Exception ignored) {}
    }

    public boolean isFirstRun() {
        return userRepo.count() == 0;
    }
}
