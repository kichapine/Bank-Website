package com.bank.service;

import com.bank.exception.BankException;
import com.bank.model.Account;
import com.bank.model.Transaction;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import jakarta.validation.ConstraintViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

@Service
public class BankService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final AccountRepository accountRepo;
    private final TransactionRepository transactionRepo;

    public BankService(AccountRepository accountRepo, TransactionRepository transactionRepo) {
        this.accountRepo = accountRepo;
        this.transactionRepo = transactionRepo;
    }


    @Transactional
    public Account createAccount(String ownerName, String email, String phone,
                                  Account.AccountType type, BigDecimal initialDeposit) {
        validateEmail(email);

        if (accountRepo.existsByEmail(email)) {
            throw BankException.duplicateEmail(email);
        }

        String accountNumber = generateAccountNumber();
        Account account = new Account(accountNumber, ownerName, email, phone, type);

        try {
            account = accountRepo.save(account);
        } catch (ConstraintViolationException e) {
            String message = e.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .findFirst()
                    .orElse("Invalid account data provided.");
            throw new BankException(message);
        }

        if (initialDeposit != null && initialDeposit.compareTo(BigDecimal.ZERO) > 0) {
            deposit(accountNumber, initialDeposit, "Initial deposit on account opening");
        }

        return accountRepo.findByAccountNumber(accountNumber).orElseThrow();

    }

    public Account getAccount(String accountNumber) {
        return accountRepo.findByAccountNumber(accountNumber)
                .orElseThrow(() -> BankException.accountNotFound(accountNumber));
    }

    public List<Account> getAllAccounts() {
        return accountRepo.findAll();
    }

    public List<Account> searchByName(String name) {
        return accountRepo.findByOwnerNameContainingIgnoreCase(name);
    }

    @Transactional
    public Account updateAccountDetails(String accountNumber, String newName,
                                         String newPhone, String newEmail) {
        Account account = getAccount(accountNumber);

        if (newName != null && !newName.isBlank()) account.setOwnerName(newName.trim());
        if (newPhone != null && !newPhone.isBlank()) account.setPhone(newPhone.trim());
        if (newEmail != null && !newEmail.isBlank()) {
            validateEmail(newEmail);
            if (!newEmail.equals(account.getEmail()) && accountRepo.existsByEmail(newEmail)) {
                throw BankException.duplicateEmail(newEmail);
            }
            account.setEmail(newEmail.trim());
        }

        return accountRepo.save(account);
    }

    @Transactional
    public Account changeAccountStatus(String accountNumber, Account.AccountStatus newStatus) {
        Account account = getAccount(accountNumber);
        account.setStatus(newStatus);
        return accountRepo.save(account);
    }

    @Transactional
    public void deleteAccount(String accountNumber) {
        Account account = getAccount(accountNumber);
        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new BankException(
                "Cannot close account with remaining balance of ₹" + account.getBalance() +
                ". Please withdraw funds first."
            );
        }
        account.setStatus(Account.AccountStatus.INACTIVE);
        accountRepo.save(account);
    }


    @Transactional
    public Transaction deposit(String accountNumber, BigDecimal amount, String description) {
        validateAmount(amount);
        Account account = getAccount(accountNumber);
        validateAccountActive(account);

        BigDecimal before = account.getBalance();
        account.setBalance(before.add(amount));
        accountRepo.save(account);

        return saveTransaction(account, Transaction.TransactionType.DEPOSIT,
                amount, before, account.getBalance(),
                description != null ? description : "Deposit", null);
    }

    @Transactional
    public Transaction withdraw(String accountNumber, BigDecimal amount, String description) {
        validateAmount(amount);
        Account account = getAccount(accountNumber);
        validateAccountActive(account);

        if (account.getBalance().compareTo(amount) < 0) {
            throw BankException.insufficientFunds(accountNumber);
        }

        BigDecimal before = account.getBalance();
        account.setBalance(before.subtract(amount));
        accountRepo.save(account);

        return saveTransaction(account, Transaction.TransactionType.WITHDRAWAL,
                amount, before, account.getBalance(),
                description != null ? description : "Withdrawal", null);
    }

    @Transactional
    public void transfer(String fromAccountNumber, String toAccountNumber,
                          BigDecimal amount, String description) {
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw BankException.selfTransfer();
        }
        validateAmount(amount);

        Account from = getAccount(fromAccountNumber);
        Account to = getAccount(toAccountNumber);
        validateAccountActive(from);
        validateAccountActive(to);

        if (from.getBalance().compareTo(amount) < 0) {
            throw BankException.insufficientFunds(fromAccountNumber);
        }

        BigDecimal fromBefore = from.getBalance();
        BigDecimal toBefore = to.getBalance();

        from.setBalance(fromBefore.subtract(amount));
        to.setBalance(toBefore.add(amount));

        accountRepo.save(from);
        accountRepo.save(to);

        String desc = description != null ? description : "Fund Transfer";

        saveTransaction(from, Transaction.TransactionType.TRANSFER_OUT,
                amount, fromBefore, from.getBalance(), desc, toAccountNumber);
        saveTransaction(to, Transaction.TransactionType.TRANSFER_IN,
                amount, toBefore, to.getBalance(), desc, fromAccountNumber);
    }

    public List<Transaction> getTransactionHistory(String accountNumber) {
        Account account = getAccount(accountNumber);
        return transactionRepo.findByAccountOrderByCreatedAtDesc(account);
    }

    public List<Transaction> getRecentTransactions(String accountNumber, int limit) {
        Account account = getAccount(accountNumber);
        return transactionRepo.findRecentByAccount(account, PageRequest.of(0, limit));
    }


    public void printBankSummary() {
        long total = accountRepo.count();
        long active = accountRepo.countActiveAccounts();
        BigDecimal totalFunds = accountRepo.totalDeposits();
        System.out.println("  Total Accounts : " + total);
        System.out.println("  Active Accounts: " + active);
        System.out.println("  Total Funds    : ₹" + (totalFunds != null ? totalFunds : BigDecimal.ZERO));
    }


    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BankException("Email address cannot be empty.");
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new BankException(
                "Invalid email address: '" + email + "'. Please enter a valid email ."
            );
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BankException.invalidAmount();
        }
    }

    private void validateAccountActive(Account account) {
        if (account.getStatus() == Account.AccountStatus.FROZEN) {
            throw BankException.accountFrozen(account.getAccountNumber());
        }
        if (account.getStatus() == Account.AccountStatus.INACTIVE) {
            throw BankException.accountInactive(account.getAccountNumber());
        }
    }

    private Transaction saveTransaction(Account account, Transaction.TransactionType type,
                                         BigDecimal amount, BigDecimal before, BigDecimal after,
                                         String description, String relatedAccount) {
        String ref = generateTransactionRef();
        Transaction tx = new Transaction(ref, account, type, amount, before, after,
                description, relatedAccount);
        return transactionRepo.save(tx);

        //Transaction has error when amt has -ve signal. yes down or up away in mines ----- solved ?
    }

    private String generateAccountNumber() {
        Random rnd = new Random();
        String number;
        do {
            number = String.format("%012d", (long)(rnd.nextDouble() * 1_000_000_000_000L));
        } while (accountRepo.existsByAccountNumber(number));
        return number;
    }



    private String generateTransactionRef() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "TXN" + ts + String.format("%04d", new Random().nextInt(9999));
    }
}
