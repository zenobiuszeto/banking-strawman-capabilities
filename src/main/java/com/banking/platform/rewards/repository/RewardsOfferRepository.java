package com.banking.platform.rewards.repository;

import com.banking.platform.rewards.model.entity.RewardsOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RewardsOfferRepository extends JpaRepository<RewardsOffer, UUID> {
    // indexed on is_active, start_date, end_date
    List<RewardsOffer> findByIsActiveTrueAndStartDateBeforeAndEndDateAfter(LocalDate startDate, LocalDate endDate);

    // indexed on offer_code (unique)
    Optional<RewardsOffer> findByOfferCode(String offerCode);
}
