package com.banking.platform.rewards.repository;

import com.banking.platform.rewards.model.entity.RewardsAccount;
import com.banking.platform.rewards.model.entity.RewardsTier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RewardsAccountRepository extends JpaRepository<RewardsAccount, UUID> {
    // indexed on customer_id (unique)
    Optional<RewardsAccount> findByCustomerId(UUID customerId);

    // indexed on tier
    Page<RewardsAccount> findByTier(RewardsTier tier, Pageable pageable);
}
