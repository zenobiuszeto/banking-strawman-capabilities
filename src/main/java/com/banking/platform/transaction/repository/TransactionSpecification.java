package com.banking.platform.transaction.repository;

import com.banking.platform.transaction.model.dto.TransactionSearchRequest;
import com.banking.platform.transaction.model.entity.Transaction;
import com.banking.platform.transaction.model.entity.TransactionCategory;
import com.banking.platform.transaction.model.entity.TransactionStatus;
import com.banking.platform.transaction.model.entity.TransactionType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Provides Spring Data Specification builders for Transaction queries.
 * Supports composable filtering with optional criteria.
 */
@Component
public class TransactionSpecification {

    /**
     * Build a specification for advanced transaction search.
     * Combines multiple optional filters with AND logic.
     *
     * @param request the search request containing filter criteria
     * @return a Specification that can be used with JpaSpecificationExecutor
     */
    public static Specification<Transaction> buildSearchSpecification(TransactionSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Required: account ID
            predicates.add(cb.equal(root.get("accountId"), request.accountId()));

            // Required: date range
            predicates.add(cb.greaterThanOrEqualTo(root.get("postDate"), request.fromDate()));
            predicates.add(cb.lessThanOrEqualTo(root.get("postDate"), request.toDate()));

            // Optional: transaction type
            if (request.type().isPresent()) {
                predicates.add(cb.equal(root.get("type"), request.type().get()));
            }

            // Optional: transaction category
            if (request.category().isPresent()) {
                predicates.add(cb.equal(root.get("category"), request.category().get()));
            }

            // Optional: transaction status
            if (request.status().isPresent()) {
                predicates.add(cb.equal(root.get("status"), request.status().get()));
            }

            // Optional: amount range
            if (request.minAmount().isPresent()) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), request.minAmount().get()));
            }

            if (request.maxAmount().isPresent()) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), request.maxAmount().get()));
            }

            // Optional: keyword search (description or merchant name)
            if (request.keyword().isPresent()) {
                String keyword = "%" + request.keyword().get().toLowerCase() + "%";
                Predicate descriptionMatch = cb.like(
                    cb.lower(root.get("description")), keyword
                );
                Predicate merchantMatch = cb.like(
                    cb.lower(root.get("merchantName")), keyword
                );
                predicates.add(cb.or(descriptionMatch, merchantMatch));
            }

            // Combine all predicates with AND
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Create a specification filtering by account ID.
     *
     * @param accountId the account ID
     * @return a Specification for this criterion
     */
    public static Specification<Transaction> byAccountId(UUID accountId) {
        return (root, query, cb) -> cb.equal(root.get("accountId"), accountId);
    }

    /**
     * Create a specification filtering by transaction type.
     *
     * @param type the transaction type
     * @return a Specification for this criterion
     */
    public static Specification<Transaction> byType(TransactionType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    /**
     * Create a specification filtering by transaction category.
     *
     * @param category the transaction category
     * @return a Specification for this criterion
     */
    public static Specification<Transaction> byCategory(TransactionCategory category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    /**
     * Create a specification filtering by transaction status.
     *
     * @param status the transaction status
     * @return a Specification for this criterion
     */
    public static Specification<Transaction> byStatus(TransactionStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * Create a specification filtering by minimum amount.
     *
     * @param minAmount the minimum amount (inclusive)
     * @return a Specification for this criterion
     */
    public static Specification<Transaction> byMinAmount(BigDecimal minAmount) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), minAmount);
    }

    /**
     * Create a specification filtering by maximum amount.
     *
     * @param maxAmount the maximum amount (inclusive)
     * @return a Specification for this criterion
     */
    public static Specification<Transaction> byMaxAmount(BigDecimal maxAmount) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("amount"), maxAmount);
    }

    /**
     * Create a specification filtering by date range.
     *
     * @param fromDate start date (inclusive)
     * @param toDate end date (inclusive)
     * @return a Specification for this criterion
     */
    public static Specification<Transaction> byDateRange(LocalDate fromDate, LocalDate toDate) {
        return (root, query, cb) -> cb.and(
            cb.greaterThanOrEqualTo(root.get("postDate"), fromDate),
            cb.lessThanOrEqualTo(root.get("postDate"), toDate)
        );
    }

    /**
     * Create a specification filtering by keyword in description or merchant name.
     *
     * @param keyword the keyword to search for (case-insensitive)
     * @return a Specification for this criterion
     */
    public static Specification<Transaction> byKeyword(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                cb.like(cb.lower(root.get("description")), pattern),
                cb.like(cb.lower(root.get("merchantName")), pattern)
            );
        };
    }
}
