package com.banking.platform.ledger.repository;

import com.banking.platform.ledger.model.entity.PostingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostingRuleRepository extends JpaRepository<PostingRule, UUID> {

    List<PostingRule> findByTriggerEventAndActiveTrue(String triggerEvent);

    Optional<PostingRule> findByRuleCode(String ruleCode);

    List<PostingRule> findByActiveTrue();

    boolean existsByRuleCode(String ruleCode);
}

