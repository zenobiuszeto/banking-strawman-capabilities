package com.banking.platform.account.repository;

import com.banking.platform.account.model.entity.Account;
import com.banking.platform.account.model.entity.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByCustomerId(UUID customerId);

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByCustomerIdAndStatus(UUID customerId, AccountStatus status);

    @Query("SELECT COALESCE(SUM(a.currentBalance), 0) FROM Account a WHERE a.customerId = :customerId")
    BigDecimal sumBalancesByCustomerId(@Param("customerId") UUID customerId);

    Page<Account> findByStatusAndCurrentBalanceLessThan(AccountStatus status, BigDecimal balance, Pageable pageable);
}
