package com.banking.platform.bank.repository;

import com.banking.platform.bank.model.entity.BankDirectory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankDirectoryRepository extends JpaRepository<BankDirectory, UUID> {

    Optional<BankDirectory> findByRoutingNumber(String routingNumber);

    Page<BankDirectory> searchByBankNameContainingIgnoreCase(String bankName, Pageable pageable);
}
