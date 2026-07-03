package com.bank.repository;

import com.bank.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findByEmail(String email);

    boolean existsByAccountNumber(String accountNumber);

    boolean existsByEmail(String email);

    List<Account> findByOwnerNameContainingIgnoreCase(String name);

    List<Account> findByStatus(Account.AccountStatus status);

    List<Account> findByAccountType(Account.AccountType type);

    @Query("SELECT COUNT(a) FROM Account a WHERE a.status = 'ACTIVE'")
    long countActiveAccounts();

    @Query("SELECT SUM(a.balance) FROM Account a WHERE a.status = 'ACTIVE'")
    java.math.BigDecimal totalDeposits();
}
