package com.banking.platform.transaction.repository;

import com.banking.platform.transaction.model.entity.Transaction;
import com.banking.platform.transaction.model.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Transaction entity.
 * Provides database access operations and advanced queries.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    /**
     * Find all transactions for an account ordered by creation date descending.
     *
     * @param accountId the account ID
     * @param pageable pagination information
     * @return paginated list of transactions
     */
    Page<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    /**
     * Find all transactions for an account within a date range.
     *
     * @param accountId the account ID
     * @param fromDate start date (inclusive)
     * @param toDate end date (inclusive)
     * @return list of transactions in the date range
     */
    List<Transaction> findByAccountIdAndPostDateBetween(UUID accountId, LocalDate fromDate, LocalDate toDate);

    /**
     * Find a transaction by its unique reference number.
     *
     * @param referenceNumber the unique reference number
     * @return optional containing the transaction if found
     */
    Optional<Transaction> findByReferenceNumber(String referenceNumber);

    /**
     * Calculate the sum of transaction amounts for a specific account, type, and date range.
     * Used for financial reporting and summaries.
     *
     * @param accountId the account ID
     * @param type the transaction type (CREDIT or DEBIT)
     * @param fromDate start date (inclusive)
     * @param toDate end date (inclusive)
     * @return sum of amounts; zero if no transactions found
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.accountId = :accountId AND t.type = :type " +
           "AND t.postDate BETWEEN :fromDate AND :toDate")
    BigDecimal sumByAccountIdAndTypeAndDateRange(
        @Param("accountId") UUID accountId,
        @Param("type") TransactionType type,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate
    );

    /**
     * Count transactions for an account within a date range.
     * Used for transaction summary statistics.
     *
     * @param accountId the account ID
     * @param fromDate start date (inclusive)
     * @param toDate end date (inclusive)
     * @return count of transactions
     */
    @Query("SELECT COUNT(t) FROM Transaction t " +
           "WHERE t.accountId = :accountId " +
           "AND t.postDate BETWEEN :fromDate AND :toDate")
    int countByAccountIdAndPostDateBetween(
        @Param("accountId") UUID accountId,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate
    );
}
