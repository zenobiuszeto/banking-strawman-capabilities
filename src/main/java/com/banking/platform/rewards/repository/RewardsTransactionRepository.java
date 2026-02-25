package com.banking.platform.rewards.repository;

import com.banking.platform.rewards.model.entity.RewardsTransaction;
import com.banking.platform.rewards.model.entity.RewardsTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface RewardsTransactionRepository extends JpaRepository<RewardsTransaction, UUID> {
    // indexed on rewards_account_id + created_at
    Page<RewardsTransaction> findByRewardsAccountIdOrderByCreatedAtDesc(UUID rewardsAccountId, Pageable pageable);

    // indexed on rewards_account_id + created_at
    List<RewardsTransaction> findByRewardsAccountIdAndCreatedAtBetween(UUID rewardsAccountId, Instant startDate, Instant endDate);

    // indexed on source_transaction_id
    List<RewardsTransaction> findBySourceTransactionId(UUID sourceTransactionId);

    @Query("SELECT COALESCE(SUM(rt.points), 0) FROM RewardsTransaction rt WHERE rt.rewardsAccountId = :rewardsAccountId AND rt.type IN :types")
    Long sumPointsByAccountIdAndTypeIn(@Param("rewardsAccountId") UUID rewardsAccountId, @Param("types") List<RewardsTransactionType> types);
}
