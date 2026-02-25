package com.banking.platform.rewards.repository;

import com.banking.platform.rewards.model.entity.RedemptionStatus;
import com.banking.platform.rewards.model.entity.RewardsRedemption;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RewardsRedemptionRepository extends JpaRepository<RewardsRedemption, UUID> {
    Page<RewardsRedemption> findByRewardsAccountId(UUID rewardsAccountId, Pageable pageable);

    List<RewardsRedemption> findByStatus(RedemptionStatus status);
}
