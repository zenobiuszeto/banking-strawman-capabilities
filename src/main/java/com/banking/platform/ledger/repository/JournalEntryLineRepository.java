package com.banking.platform.ledger.repository;

import com.banking.platform.ledger.model.entity.JournalEntryLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JournalEntryLineRepository extends JpaRepository<JournalEntryLine, UUID> {

    List<JournalEntryLine> findByGlAccountId(UUID glAccountId);

    @Query("SELECT jel.accountCode, " +
            "COALESCE(SUM(jel.debitAmount), 0), " +
            "COALESCE(SUM(jel.creditAmount), 0) " +
            "FROM JournalEntryLine jel " +
            "JOIN jel.journalEntry je " +
            "WHERE je.status = 'POSTED' " +
            "GROUP BY jel.accountCode " +
            "ORDER BY jel.accountCode")
    List<Object[]> findTrialBalanceData();

    @Query("SELECT COALESCE(SUM(jel.debitAmount), 0), COALESCE(SUM(jel.creditAmount), 0) " +
            "FROM JournalEntryLine jel " +
            "JOIN jel.journalEntry je " +
            "WHERE je.status = 'POSTED' AND jel.accountCode = :accountCode")
    Object[] findBalanceByAccountCode(@Param("accountCode") String accountCode);
}

