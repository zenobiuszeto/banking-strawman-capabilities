package com.banking.platform.debitnetwork.repository;

import com.banking.platform.debitnetwork.model.entity.DebitTransaction;
import com.banking.platform.debitnetwork.model.entity.DebitTransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DebitTransactionRepository extends JpaRepository<DebitTransaction, UUID> {
    Optional<DebitTransaction> findByAuthorizationCode(String authorizationCode);
    Page<DebitTransaction> findByDebitCardIdOrderByAuthorizedAtDesc(UUID debitCardId, Pageable pageable);
    Page<DebitTransaction> findByAccountIdOrderByAuthorizedAtDesc(UUID accountId, Pageable pageable);
    List<DebitTransaction> findByStatusAndAuthorizedAtBefore(DebitTransactionStatus status, Instant before);
    List<DebitTransaction> findByStatus(DebitTransactionStatus status);

    @Query("SELECT COALESCE(SUM(dt.amount), 0) FROM DebitTransaction dt " +
            "WHERE dt.debitCardId = :cardId AND dt.status IN ('AUTHORIZED', 'SETTLED') " +
            "AND dt.authorizedAt >= :since")
    BigDecimal sumAmountByCardIdSince(@Param("cardId") UUID cardId, @Param("since") Instant since);
}

