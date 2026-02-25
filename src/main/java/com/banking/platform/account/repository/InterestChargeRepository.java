package com.banking.platform.account.repository;

import com.banking.platform.account.model.entity.ChargeType;
import com.banking.platform.account.model.entity.InterestCharge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface InterestChargeRepository extends JpaRepository<InterestCharge, UUID> {

    Page<InterestCharge> findByAccountIdOrderByPostDateDesc(UUID accountId, Pageable pageable);

    List<InterestCharge> findByAccountIdAndPostDateBetween(UUID accountId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COALESCE(SUM(ic.amount), 0) FROM InterestCharge ic WHERE ic.accountId = :accountId AND ic.chargeType = :chargeType")
    BigDecimal sumChargesByAccountIdAndType(@Param("accountId") UUID accountId, @Param("chargeType") ChargeType chargeType);
}
