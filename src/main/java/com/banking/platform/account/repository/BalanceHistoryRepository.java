package com.banking.platform.account.repository;

import com.banking.platform.account.model.entity.BalanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BalanceHistoryRepository extends JpaRepository<BalanceHistory, UUID> {

    List<BalanceHistory> findByAccountIdAndSnapshotDateBetween(UUID accountId, LocalDate startDate, LocalDate endDate);

    Optional<BalanceHistory> findTopByAccountIdOrderBySnapshotDateDesc(UUID accountId);
}
