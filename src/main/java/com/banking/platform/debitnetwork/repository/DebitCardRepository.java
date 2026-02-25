package com.banking.platform.debitnetwork.repository;

import com.banking.platform.debitnetwork.model.entity.CardStatus;
import com.banking.platform.debitnetwork.model.entity.DebitCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DebitCardRepository extends JpaRepository<DebitCard, UUID> {
    List<DebitCard> findByCustomerId(UUID customerId);
    List<DebitCard> findByAccountId(UUID accountId);
    List<DebitCard> findByCustomerIdAndStatus(UUID customerId, CardStatus status);
    boolean existsByAccountIdAndStatus(UUID accountId, CardStatus status);
}

