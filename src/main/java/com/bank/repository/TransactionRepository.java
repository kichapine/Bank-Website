package com.bank.repository;

import com.bank.model.Account;
import com.bank.model.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountOrderByCreatedAtDesc(Account account);

    @Query("SELECT t FROM Transaction t WHERE t.account = :account ORDER BY t.createdAt DESC")
    List<Transaction> findRecentByAccount(Account account, Pageable pageable);

    long countByAccount(Account account);
}
