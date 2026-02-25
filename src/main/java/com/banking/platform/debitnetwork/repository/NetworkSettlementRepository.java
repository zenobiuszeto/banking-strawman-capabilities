package com.banking.platform.debitnetwork.repository;

import com.banking.platform.debitnetwork.model.entity.NetworkSettlement;
import com.banking.platform.debitnetwork.model.entity.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NetworkSettlementRepository extends JpaRepository<NetworkSettlement, UUID> {
    Optional<NetworkSettlement> findByBatchId(String batchId);
    Page<NetworkSettlement> findByStatusOrderBySettlementDateDesc(SettlementStatus status, Pageable pageable);
    List<NetworkSettlement> findBySettlementDate(LocalDate date);
    Page<NetworkSettlement> findAllByOrderBySettlementDateDesc(Pageable pageable);
}

