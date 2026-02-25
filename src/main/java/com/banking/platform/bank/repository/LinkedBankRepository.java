package com.banking.platform.bank.repository;

import com.banking.platform.bank.model.entity.LinkedBank;
import com.banking.platform.bank.model.entity.LinkStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LinkedBankRepository extends JpaRepository<LinkedBank, UUID> {

    List<LinkedBank> findByCustomerId(UUID customerId);

    List<LinkedBank> findByCustomerIdAndLinkStatus(UUID customerId, LinkStatus linkStatus);

    Optional<LinkedBank> findByCustomerIdAndIsPrimaryTrue(UUID customerId);

    boolean existsByCustomerIdAndRoutingNumberAndAccountNumber(
            UUID customerId,
            String routingNumber,
            String accountNumber
    );
}
