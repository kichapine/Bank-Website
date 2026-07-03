package com.bank.exception;

public class BankException extends RuntimeException {

    public BankException(String message) {
        super(message);
    }

    public BankException(String message, Throwable cause) {
        super(message, cause);
    }

    public static BankException accountNotFound(String accountNumber) {
        return new BankException("Account not found: " + accountNumber);
    }

    public static BankException insufficientFunds(String accountNumber) {
        return new BankException("Insufficient funds in account: " + accountNumber);
    }

    public static BankException accountFrozen(String accountNumber) {
        return new BankException("Account is frozen: " + accountNumber);
    }

    public static BankException accountInactive(String accountNumber) {
        return new BankException("Account is inactive/closed: " + accountNumber);
    }

    public static BankException duplicateEmail(String email) {
        return new BankException("Email already registered: " + email);
    }

    public static BankException invalidAmount() {
        return new BankException("Amount must be greater than zero.");
    }

    public static BankException selfTransfer() {
        return new BankException("Cannot transfer to the same account.");
    }
}
