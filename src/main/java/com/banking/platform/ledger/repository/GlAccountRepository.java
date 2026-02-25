package com.banking.platform.ledger.repository;

import com.banking.platform.ledger.model.entity.GlAccount;
import com.banking.platform.ledger.model.entity.GlAccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GlAccountRepository extends JpaRepository<GlAccount, UUID> {

    Optional<GlAccount> findByAccountCode(String accountCode);

    List<GlAccount> findByAccountType(GlAccountType accountType);

    List<GlAccount> findByActiveTrue();

    List<GlAccount> findByParentId(UUID parentId);

    boolean existsByAccountCode(String accountCode);
}

