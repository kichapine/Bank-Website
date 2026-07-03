package com.bank.runner;

import com.bank.exception.BankException;
import com.bank.model.Account;
import com.bank.model.AuditLog;
import com.bank.model.Transaction;
import com.bank.model.User;
import com.bank.security.SessionContext;
import com.bank.service.AuthService;
import com.bank.service.BankService;
import jakarta.validation.ConstraintViolationException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

@Component
public class ConsoleRunner implements CommandLineRunner {

    private static final String DIVIDER  = "═══════════════════════════════════════════════════";
    private static final String THIN_DIV = "───────────────────────────────────────────────────";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final BankService    bankService;
    private final AuthService    authService;
    private final SessionContext session;
    private final Scanner        scanner = new Scanner(System.in);

    public ConsoleRunner(BankService bankService, AuthService authService, SessionContext session) {
        this.bankService = bankService;
        this.authService = authService;
        this.session     = session;
    }

    @Override
    public void run(String... args) {
        printWelcome();
        if (authService.isFirstRun()) {
            info("No users found. Let's create the first Admin account.");
            bootstrapAdmin();
        }
        boolean running = true;
        while (running) {
            if (!session.isLoggedIn()) {
                printAuthMenu();
                String choice = prompt("Enter choice");
                switch (choice) {
                    case "1" -> login();
                    case "0" -> { printBye(); running = false; }
                    default  -> error("Invalid option.");
                }
            } else {
                printMainMenu();
                String choice = prompt("Enter choice");
                running = handleMainMenu(choice);
            }
        }
        scanner.close();
    }

    private boolean handleMainMenu(String choice) {
        switch (choice) {
            case "1"  -> createAccount();
            case "2"  -> viewAccount();
            case "3"  -> listAllAccounts();
            case "4"  -> myAccounts();
            case "5"  -> switchAccount();
            case "6"  -> updateAccount();
            case "7"  -> deposit();
            case "8"  -> withdraw();
            case "9"  -> transfer();
            case "10" -> viewTransactionHistory();
            case "11" -> manageAccountStatus();
            case "12" -> closeAccount();
            case "13" -> bankSummary();
            case "14" -> createUserMenu();
            case "15" -> linkAccountMenu();
            case "16" -> changePassword();
            case "17" -> viewAuditLogs();
            case "18" -> manageUsers();
            case "19" -> viewSessionInfo();
            case "0"  -> { logout(); return true; }
            default   -> error("Invalid option. Please try again.");
        }
        return true;
    }


    private void bootstrapAdmin() {
        section("CREATE FIRST ADMIN");
        System.out.println("  Password policy: 8+ chars, uppercase, digit, special char.");
        try {
            String username = prompt("Admin Username");
            String password = promptPassword("Admin Password");
            String confirm  = promptPassword("Confirm Password");
            if (!password.equals(confirm)) { error("Passwords do not match."); return; }
            authService.registerAdmin(username, password);
            success("Admin account created. Please log in.");
        } catch (BankException | ConstraintViolationException e) {
            error(e.getMessage());
            bootstrapAdmin();
        }
    }

    private void login() {
        section("LOGIN");
        try {
            String username = prompt("Username");
            String password = promptPassword("Password");
            authService.login(username, password);
            success("Welcome back, " + session.getUsername() + "! Role: " + session.getRole());
            // If customer has multiple accounts, show them and let them pick
            if (session.getRole() == User.Role.CUSTOMER && session.getAccounts() != null) {
                List<Account> accs = session.getAccounts();
                if (accs.size() > 1) {
                    info("You have " + accs.size() + " accounts. Active: " + session.getActiveAccountNumber());
                    System.out.println("  Your accounts:");
                    for (int i = 0; i < accs.size(); i++) {
                        System.out.printf("    %d. %s [%s] ₹%.2f%n",
                                i + 1, accs.get(i).getAccountNumber(),
                                accs.get(i).getAccountType(), accs.get(i).getBalance());
                    }
                    String pick = promptOptional("Switch active account? Enter number or blank to keep " +
                                                 session.getActiveAccountNumber());
                    if (pick != null) {
                        int idx = Integer.parseInt(pick) - 1;
                        if (idx >= 0 && idx < accs.size()) {
                            session.switchAccount(accs.get(idx).getAccountNumber());
                            success("Active account set to: " + session.getActiveAccountNumber());
                        }
                    }
                }
            }
        } catch (BankException | ConstraintViolationException e) {
            error(e.getMessage());
        }
    }

    private void logout() {
        try {
            authService.logout();
            success("Logged out successfully.");
        } catch (Exception e) {
            error(e.getMessage());
        }
    }


    // ts shows all the accounts linked to the particular user
    private void myAccounts() {
        section("MY ACCOUNTS");
        try {
            session.requireRole(User.Role.CUSTOMER);
            List<Account> accs = session.getAccounts();
            if (accs == null || accs.isEmpty()) { info("No accounts linked."); return; }
            System.out.printf("  %-14s %-10s %-12s %-10s %-10s%n",
                    "ACCOUNT NO.", "TYPE", "BALANCE", "STATUS", "");
            System.out.println("  " + THIN_DIV);
            for (Account a : accs) {
                String active = a.getAccountNumber().equals(session.getActiveAccountNumber()) ? "← ACTIVE" : "";
                System.out.printf("  %-14s %-10s ₹%-11.2f %-10s %s%n",
                        a.getAccountNumber(), a.getAccountType(),
                        a.getBalance(), a.getStatus(), active);
            }
        } catch (SecurityException e) {
            error(e.getMessage());
        }
    }

    private void switchAccount() {
        section("SWITCH ACTIVE ACCOUNT");
        try {
            session.requireRole(User.Role.CUSTOMER);
            List<Account> accs = session.getAccounts();
            if (accs == null || accs.size() <= 1) {
                info("You only have one account — nothing to switch.");
                return;
            }
            System.out.println("  Your accounts:");
            for (int i = 0; i < accs.size(); i++) {
                String active = accs.get(i).getAccountNumber().equals(session.getActiveAccountNumber())
                        ? " ← currently active" : "";
                System.out.printf("    %d. %s [%s] ₹%.2f%s%n",
                        i + 1, accs.get(i).getAccountNumber(),
                        accs.get(i).getAccountType(), accs.get(i).getBalance(), active);
            }
            String pick = prompt("Select account number (1-" + accs.size() + ")");
            int idx = Integer.parseInt(pick) - 1;
            if (idx < 0 || idx >= accs.size()) { error("Invalid selection."); return; }
            session.switchAccount(accs.get(idx).getAccountNumber());
            success("Active account switched to: " + session.getActiveAccountNumber());
        } catch (SecurityException | BankException | NumberFormatException e) {
            error(e.getMessage());
        }
    }


    private void createUserMenu() {
        section("CREATE USER");
        try {
            session.requireRole(User.Role.ADMIN, User.Role.TELLER);
            System.out.println("  1. Create Teller (Admin only)");
            System.out.println("  2. Create Customer (link to existing account)");
            String choice = prompt("Choose type");

            String username = prompt("New Username");
            String password = promptPassword("Password");
            String confirm  = promptPassword("Confirm Password");
            if (!password.equals(confirm)) { error("Passwords do not match."); return; }

            if (choice.equals("1")) {
                User u = authService.registerTeller(username, password);
                success("Teller user created: " + u.getUsername());
            } else {
                String accNum = prompt("Account Number to link");
                Account account = bankService.getAccount(accNum);
                User u = authService.registerCustomer(username, password, account);
                success("Customer user created: " + u.getUsername() + " → " + accNum);
            }
        } catch (SecurityException | BankException | ConstraintViolationException e) {
            error(e.getMessage());
        }
    }


    private void linkAccountMenu() {
        section("LINK ACCOUNT TO USER");
        try {
            session.requireRole(User.Role.ADMIN, User.Role.TELLER);
            String username = prompt("Customer Username");
            String accNum   = prompt("Account Number to link");
            Account account = bankService.getAccount(accNum);
            authService.linkAccountToUser(username, account);
            success("Account " + accNum + " linked to user: " + username);
            info("They can now access both accounts after logging in.");
        } catch (SecurityException | BankException | ConstraintViolationException e) {
            error(e.getMessage());
        }
    }

    private void changePassword() {
        section("CHANGE PASSWORD");
        try {
            session.requireAuth();
            String target = prompt("Username (leave blank for yourself)");
            if (target.isBlank()) target = session.getUsername();
            String oldPwd  = promptPassword("Current Password");
            String newPwd  = promptPassword("New Password");
            String confirm = promptPassword("Confirm New Password");
            if (!newPwd.equals(confirm)) { error("Passwords do not match."); return; }
            authService.changePassword(target, oldPwd, newPwd);
            success("Password changed successfully.");
        } catch (SecurityException | BankException | ConstraintViolationException e) {
            error(e.getMessage());
        }
    }

    private void viewAuditLogs() {
        section("AUDIT LOGS");
        try {
            session.requireRole(User.Role.ADMIN);
            String limitStr = promptOptional("Number of recent logs to show (default 20)");
            int limit = (limitStr != null) ? Integer.parseInt(limitStr) : 20;
            List<AuditLog> logs = authService.getAuditLogs(limit);
            if (logs.isEmpty()) { info("No audit logs yet."); return; }
            System.out.printf("  %-20s %-12s %-20s %-8s%n", "DATE/TIME", "USER", "EVENT", "OK?");
            System.out.println("  " + THIN_DIV);
            for (AuditLog log : logs) {
                System.out.printf("  %-20s %-12s %-20s %-8s%n",
                        log.getCreatedAt().format(FMT),
                        truncate(log.getUsername() != null ? log.getUsername() : "-", 12),
                        log.getEventType(), log.isSuccess() ? "✔" : "✘");
                if (log.getDescription() != null)
                    System.out.println("                       " + log.getDescription());
            }
        } catch (SecurityException e) {
            error(e.getMessage());
        }
    }

    private void manageUsers() {
        section("MANAGE USERS");
        try {
            session.requireRole(User.Role.ADMIN);
            List<User> users = authService.listAllUsers();
            System.out.printf("  %-5s %-20s %-10s %-12s %-8s %-6s%n",
                    "ID", "USERNAME", "ROLE", "LAST LOGIN", "ACTIVE", "ACCS");
            System.out.println("  " + THIN_DIV);
            for (User u : users) {
                System.out.printf("  %-5d %-20s %-10s %-12s %-8s %-6d%n",
                        u.getId(), u.getUsername(), u.getRole(),
                        u.getLastLogin() != null ? u.getLastLogin().format(
                                DateTimeFormatter.ofPattern("dd-MM HH:mm")) : "Never",
                        u.isActive() ? "Yes" : "No",
                        u.getAccounts().size());
                if (u.isLocked())
                    System.out.println("         LOCKED until " + u.getLockedUntil().format(FMT));
                if (!u.getAccounts().isEmpty()) {
                    u.getAccounts().forEach(a ->
                        System.out.println("           Account: " + a.getAccountNumber() + " [" + a.getAccountType() + "]"));
                }
            }
            System.out.println("\n  Actions: D=Deactivate, U=Unlock, Enter=Back");
            String action = prompt("Action [D/U/Enter to skip]").toUpperCase();
            if (action.equals("D")) {
                authService.deactivateUser(prompt("Username to deactivate"));
                success("User deactivated.");
            } else if (action.equals("U")) {
                authService.unlockUser(prompt("Username to unlock"));
                success("User unlocked.");
            }
        } catch (SecurityException | BankException | ConstraintViolationException e) {
            error(e.getMessage());
        }
    }

    private void viewSessionInfo() {
        section("SESSION INFO");
        System.out.println("  " + session.getSessionInfo());
        if (session.isLoggedIn() && session.getRole() == User.Role.CUSTOMER) {
            List<Account> accs = session.getAccounts();
            if (accs != null && !accs.isEmpty()) {
                System.out.println("  Your accounts:");
                accs.forEach(a -> {
                    String active = a.getAccountNumber().equals(session.getActiveAccountNumber())
                            ? " ← ACTIVE" : "";
                    System.out.printf("    %s [%s] ₹%.2f%s%n",
                            a.getAccountNumber(), a.getAccountType(), a.getBalance(), active);
                });
            }
        }
    }


    private void createAccount() {
        section("CREATE NEW ACCOUNT");
        try {
            session.requireRole(User.Role.ADMIN, User.Role.TELLER);
            String name   = prompt("Full Name");
            String email  = prompt("Email Address");
            String phone  = prompt("Phone Number");
            String type   = prompt("Account Type [1=SAVINGS, 2=CURRENT]");
            Account.AccountType accType = type.equals("2")
                    ? Account.AccountType.CURRENT : Account.AccountType.SAVINGS;
            String depStr = prompt("Initial Deposit Amount (0 to skip)");
            BigDecimal deposit = parseMoney(depStr);
            Account account = bankService.createAccount(name, email, phone, accType, deposit);
            success("Account created successfully!");
            printAccountDetails(account);

            String createLogin = prompt("Create login credentials for this customer? (yes/no)");
            if (createLogin.equalsIgnoreCase("yes")) {
                String existingUser = promptOptional("Link to existing username? (blank to create new)");
                if (existingUser != null) {
                    authService.linkAccountToUser(existingUser, account);
                    success("Account linked to existing user: " + existingUser);
                } else {
                    String username = prompt("New Username");
                    String password = promptPassword("Password");
                    String confirm  = promptPassword("Confirm Password");
                    if (!password.equals(confirm)) { error("Passwords do not match."); return; }
                    authService.registerCustomer(username, password, account);
                    success("Customer login created: " + username);
                }
            }
        } catch (SecurityException | BankException | ConstraintViolationException e) {
            error(e.getMessage());
        }
    }

    private void viewAccount() {
        section("VIEW ACCOUNT");
        try {
            session.requireAuth();
            String accNum = resolveAccountNumber();
            session.requireAccountAccess(accNum);
            printAccountDetails(bankService.getAccount(accNum));
        } catch (SecurityException | BankException | ConstraintViolationException e) {
            error(e.getMessage());
        }
    }

    private void listAllAccounts() {
        section("ALL ACCOUNTS");
        try {
            session.requireRole(User.Role.ADMIN, User.Role.TELLER);
            List<Account> accounts = bankService.getAllAccounts();
            if (accounts.isEmpty()) { info("No accounts found."); return; }
            System.out.printf("  %-14s %-20s %-10s %-12s %-10s%n",
                    "ACCOUNT NO.", "OWNER", "TYPE", "BALANCE", "STATUS");
            System.out.println("  " + THIN_DIV);
            for (Account a : accounts) {
                System.out.printf("  %-14s %-20s %-10s ₹%-11.2f %-10s%n",
                        a.getAccountNumber(), truncate(a.getOwnerName(), 20),
                        a.getAccountType(), a.getBalance(), a.getStatus());
            }
            System.out.println("  Total: " + accounts.size() + " account(s)");
        } catch (SecurityException e) {
            error(e.getMessage());
        }
    }

    private void updateAccount() {
        section("UPDATE ACCOUNT");
        try {
            session.requireAuth();
            String accNum = resolveAccountNumber();
            session.requireAccountAccess(accNum);
            Account current = bankService.getAccount(accNum);
            System.out.println("  Leave blank to keep current value.");
            System.out.println("  Current Name : " + current.getOwnerName());
            String newName  = promptOptional("New Name");
            System.out.println("  Current Email: " + current.getEmail());
            String newEmail = promptOptional("New Email");
            System.out.println("  Current Phone: " + current.getPhone());
            String newPhone = promptOptional("New Phone");
            Account updated = bankService.updateAccountDetails(accNum, newName, newPhone, newEmail);
            success("Account updated successfully!");
            printAccountDetails(updated);
        } catch (SecurityException | BankException | ConstraintViolationException e) {
            error(e.getMessage());
        }
    }


    private void deposit() {
        section("DEPOSIT");
        try {
            session.requireRole(User.Role.ADMIN, User.Role.TELLER);
            String accNum = prompt("Enter Account Number");
            String amtStr = prompt("Enter Amount");
            String desc   = promptOptional("Description (optional)");
            Transaction tx = bankService.deposit(accNum, parseMoney(amtStr), desc);
            success("Deposit successful! New Balance: ₹" + tx.getBalanceAfter());
            printTransaction(tx);
        } catch (SecurityException | BankException | ConstraintViolationException e) {
            error(e.getMessage());
        }
    }

    private void withdraw() {
        section("WITHDRAW");
        try {
            session.requireAuth();
            String accNum = resolveAccountNumber();
            session.requireAccountAccess(accNum);
            String amtStr = prompt("Enter Amount");
            String desc   = promptOptional("Description (optional)");
            Transaction tx = bankService.withdraw(accNum, parseMoney(amtStr), desc);
            success("Withdrawal successful! New Balance: ₹" + tx.getBalanceAfter());
            printTransaction(tx);
        } catch (SecurityException | BankException | ConstraintViolationException e) {
            error(e.getMessage());
        }
    }

    private void transfer() {
        section("TRANSFER FUNDS");
        try {
            session.requireAuth();
            String fromAcc;
            if (session.getRole() == User.Role.CUSTOMER && session.getAccounts() != null
                    && session.getAccounts().size() > 1) {
                System.out.println("  Transfer from which account?");
                List<Account> accs = session.getAccounts();
                for (int i = 0; i < accs.size(); i++) {
                    System.out.printf("    %d. %s [%s] ₹%.2f%n", i + 1,
                            accs.get(i).getAccountNumber(), accs.get(i).getAccountType(),
                            accs.get(i).getBalance());
                }
                String pick = prompt("Select (1-" + accs.size() + ") or press Enter for active account");
                if (pick.isBlank()) {
                    fromAcc = session.getActiveAccountNumber();
                } else {
                    fromAcc = accs.get(Integer.parseInt(pick) - 1).getAccountNumber();
                }
            } else {
                fromAcc = resolveAccountNumber();
            }
            session.requireAccountAccess(fromAcc);
            String toAcc  = prompt("To Account Number");
            String amtStr = prompt("Amount");
            String desc   = promptOptional("Description (optional)");
            bankService.transfer(fromAcc, toAcc, parseMoney(amtStr), desc);
            success("Transfer completed: " + fromAcc + " → " + toAcc);
        } catch (SecurityException | BankException | ConstraintViolationException | NumberFormatException e) {
            error(e.getMessage());
        }
    }

    private void viewTransactionHistory() {
        section("TRANSACTION HISTORY");
        try {
            session.requireAuth();
            String accNum;
            if (session.getRole() == User.Role.CUSTOMER && session.getAccounts() != null
                    && session.getAccounts().size() > 1) {
                System.out.println("  View history for which account?");
                List<Account> accs = session.getAccounts();
                for (int i = 0; i < accs.size(); i++) {
                    System.out.printf("    %d. %s [%s]%n", i + 1,
                            accs.get(i).getAccountNumber(), accs.get(i).getAccountType());
                }
                String pick = prompt("Select (1-" + accs.size() + ") or Enter for active");
                accNum = pick.isBlank()
                        ? session.getActiveAccountNumber()
                        : accs.get(Integer.parseInt(pick) - 1).getAccountNumber();
            } else {
                accNum = resolveAccountNumber();
            }
            session.requireAccountAccess(accNum);
            String limitStr = promptOptional("Show last N transactions (blank for all)");
            List<Transaction> txns = limitStr != null
                    ? bankService.getRecentTransactions(accNum, Integer.parseInt(limitStr))
                    : bankService.getTransactionHistory(accNum);
            if (txns.isEmpty()) { info("No transactions found."); return; }
            Account account = bankService.getAccount(accNum);
            System.out.println("  Account: " + accNum + " | Owner: " + account.getOwnerName());
            System.out.println("  " + THIN_DIV);
            System.out.printf("  %-20s %-14s %-12s %-12s %-12s%n",
                    "DATE/TIME", "TYPE", "AMOUNT", "BEFORE", "AFTER");
            System.out.println("  " + THIN_DIV);
            for (Transaction tx : txns) {
                System.out.printf("  %-20s %-14s ₹%-11.2f ₹%-11.2f ₹%-11.2f%n",
                        tx.getCreatedAt().format(FMT), tx.getType(),
                        tx.getAmount(), tx.getBalanceBefore(), tx.getBalanceAfter());
                if (tx.getRelatedAccountNumber() != null)
                    System.out.println("                       Related A/C: " + tx.getRelatedAccountNumber());
                if (tx.getDescription() != null && !tx.getDescription().isBlank())
                    System.out.println("                       Note: " + tx.getDescription());
            }
            System.out.println("  Total: " + txns.size() + " transaction(s)");
        } catch (SecurityException | BankException | ConstraintViolationException | NumberFormatException e) {
            error(e.getMessage());
        }
    }


    private void manageAccountStatus() {
        section("MANAGE ACCOUNT STATUS");
        try {
            session.requireRole(User.Role.ADMIN, User.Role.TELLER);
            String accNum = prompt("Enter Account Number");
            Account account = bankService.getAccount(accNum);
            System.out.println("  Current Status: " + account.getStatus());
            System.out.println("  1. ACTIVE  2. FROZEN");
            String choice = prompt("Set status to");
            Account.AccountStatus newStatus = switch (choice) {
                case "1" -> Account.AccountStatus.ACTIVE;
                case "2" -> Account.AccountStatus.FROZEN;
                default  -> throw new BankException("Invalid status option.");
            };
            bankService.changeAccountStatus(accNum, newStatus);
            success("Account status updated to: " + newStatus);
        } catch (SecurityException | BankException | ConstraintViolationException e) {
            error(e.getMessage());
        }
    }

    private void closeAccount() {
        section("CLOSE ACCOUNT");
        try {
            session.requireRole(User.Role.ADMIN);
            String accNum = prompt("Enter Account Number");
            Account account = bankService.getAccount(accNum);
            System.out.println("  Account: " + account.getOwnerName() + " | Balance: ₹" + account.getBalance());
            String confirm = prompt("Confirm close? (yes/no)");
            if (confirm.equalsIgnoreCase("yes")) {
                bankService.deleteAccount(accNum);
                success("Account closed successfully.");
            } else {
                info("Operation cancelled.");
            }
        } catch (SecurityException | BankException | ConstraintViolationException e) {
            error(e.getMessage());
        }
    }

    private void bankSummary() {
        section("BANK SUMMARY");
        try {
            session.requireRole(User.Role.ADMIN, User.Role.TELLER);
            bankService.printBankSummary();
        } catch (SecurityException e) {
            error(e.getMessage());
        }
    }


    private void printWelcome() {
        System.out.println("\n" + DIVIDER);
        System.out.println("  Welcome to Krishna Bank!");
        System.out.println(DIVIDER);
    }

    private void printAuthMenu() {
        System.out.println("\n" + DIVIDER);
        System.out.println("   1. Login");
        System.out.println("   0. Exit");
        System.out.println(DIVIDER);
    }

    private void printMainMenu() {
        String roleTag = "[" + session.getRole() + ": " + session.getUsername() + "]";
        if (session.getActiveAccountNumber() != null)
            roleTag += " | Active A/C: " + session.getActiveAccountNumber();
        System.out.println("\n" + DIVIDER);
        System.out.println("  MAIN MENU  " + roleTag);
        System.out.println(DIVIDER);

        boolean isCustomer = session.getRole() == User.Role.CUSTOMER;
        boolean isTeller   = session.getRole() == User.Role.TELLER;
        boolean isAdmin    = session.getRole() == User.Role.ADMIN;

        System.out.println("  ACCOUNT MANAGEMENT");
        if (isAdmin || isTeller) System.out.println("   1.  Create New Account");
        System.out.println("   2.  View Account Details");
        if (isAdmin || isTeller) System.out.println("   3.  List All Accounts");
        if (isCustomer) System.out.println("   4.  My Accounts");
        if (isCustomer) System.out.println("   5.  Switch Active Account");
        System.out.println("   6.  Update Account Info");
        System.out.println(THIN_DIV);
        System.out.println("  TRANSACTIONS");
        if (isAdmin || isTeller) System.out.println("   7.  Deposit");
        System.out.println("   8.  Withdraw");
        System.out.println("   9.  Transfer Funds");
        System.out.println("   10. Transaction History");
        System.out.println(THIN_DIV);
        if (isAdmin || isTeller) {
            System.out.println("  ADMIN");
            System.out.println("   11. Manage Account Status");
            if (isAdmin) System.out.println("   12. Close Account");
            System.out.println("   13. Bank Summary");
            System.out.println(THIN_DIV);
        }
        System.out.println("  USER & SECURITY");
        if (isAdmin || isTeller) System.out.println("   14. Create User");
        if (isAdmin || isTeller) System.out.println("   15. Link Account to User");
        System.out.println("   16. Change Password");
        if (isAdmin) System.out.println("   17. View Audit Logs");
        if (isAdmin) System.out.println("   18. Manage Users");
        System.out.println("   19. Session Info");
        System.out.println(THIN_DIV);
        System.out.println("   0.  Logout");
        System.out.println(DIVIDER);
    }

    private void printBye() {
        System.out.println("\n" + DIVIDER);
        System.out.println("  Thank you for using Krishna Bank!");
        System.out.println(DIVIDER);
    }


    private void printAccountDetails(Account a) {
        System.out.println("  " + THIN_DIV);
        System.out.println("  Account Number : " + a.getAccountNumber());
        System.out.println("  Owner          : " + a.getOwnerName());
        System.out.println("  Email          : " + a.getEmail());
        System.out.println("  Phone          : " + (a.getPhone() != null ? a.getPhone() : "N/A"));
        System.out.println("  Account Type   : " + a.getAccountType());
        System.out.printf( "  Balance        : ₹%.2f%n", a.getBalance());
        System.out.println("  Status         : " + a.getStatus());
        System.out.println("  Created        : " + a.getCreatedAt().format(FMT));
        System.out.println("  " + THIN_DIV);
    }

    private void printTransaction(Transaction tx) {
        System.out.println("  Ref       : " + tx.getTransactionRef());
        System.out.println("  Type      : " + tx.getType());
        System.out.printf( "  Amount    : ₹%.2f%n", tx.getAmount());
        System.out.printf( "  Before    : ₹%.2f%n", tx.getBalanceBefore());
        System.out.printf( "  After     : ₹%.2f%n", tx.getBalanceAfter());
        System.out.println("  Date/Time : " + tx.getCreatedAt().format(FMT));
    }


    private String resolveAccountNumber() {
        if (session.getRole() == User.Role.CUSTOMER) {
            List<Account> accs = session.getAccounts();
            if (accs != null && accs.size() == 1) {
                return accs.get(0).getAccountNumber();
            } else if (accs != null && accs.size() > 1) {
                return session.getActiveAccountNumber();
            }
        }
        return prompt("Enter Account Number");
    }

    private String prompt(String message) {
        System.out.print("  >> " + message + ": ");
        return scanner.nextLine().trim();
    }

    private String promptOptional(String message) {
        System.out.print("  >> " + message + ": ");
        String val = scanner.nextLine().trim();
        return val.isBlank() ? null : val;
    }

    private String promptPassword(String message) {
        java.io.Console console = System.console();
        if (console != null) {
            char[] pwd = console.readPassword("  >> " + message + ": ");
            return pwd != null ? new String(pwd) : "";
        } else {
            System.out.print("  >> " + message + " (visible - no terminal): ");
            return scanner.nextLine().trim();
        }
    }

    private void section(String title) {
        System.out.println("\n" + DIVIDER);
        System.out.println("     " + title);
        System.out.println(DIVIDER);
    }

    private void success(String msg) { System.out.println("\n  ✔  " + msg); }
    private void error(String msg)   { System.out.println("\n  ✘  ERROR: " + msg); }
    private void info(String msg)    { System.out.println("\n  ℹ  " + msg); }

    private BigDecimal parseMoney(String str) {
        try {
            return new BigDecimal(str.replace(",", "").trim());
        } catch (NumberFormatException e) {
            throw new BankException("Invalid amount: '" + str + "'");
        }
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
