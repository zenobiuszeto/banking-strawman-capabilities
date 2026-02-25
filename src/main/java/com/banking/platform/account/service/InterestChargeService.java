package com.banking.platform.account.service;

import com.banking.platform.account.mapper.AccountMapper;
import com.banking.platform.account.model.dto.InterestChargeResponse;
import com.banking.platform.account.model.entity.ChargeType;
import com.banking.platform.account.model.entity.InterestCharge;
import com.banking.platform.account.repository.InterestChargeRepository;
import com.banking.platform.shared.exception.ResourceNotFoundException;
import com.banking.platform.shared.util.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterestChargeService {

    private final InterestChargeRepository interestChargeRepository;
    private final AccountMapper accountMapper;

    public PagedResponse<InterestChargeResponse> getInterestCharges(UUID accountId, int page, int size) {
        log.info("Fetching interest charges for account: {} with page: {}, size: {}", accountId, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<InterestCharge> chargePage = interestChargeRepository.findByAccountIdOrderByPostDateDesc(accountId, pageable);

        List<InterestChargeResponse> content = chargePage.getContent()
            .stream()
            .map(accountMapper::toInterestChargeResponse)
            .collect(Collectors.toList());

        return new PagedResponse<>(
            content,
            chargePage.getNumber(),
            chargePage.getSize(),
            chargePage.getTotalElements(),
            chargePage.getTotalPages()
        );
    }

    public List<InterestChargeResponse> getChargesByDateRange(UUID accountId, LocalDate from, LocalDate to) {
        log.info("Fetching charges for account: {} between {} and {}", accountId, from, to);

        return interestChargeRepository.findByAccountIdAndPostDateBetween(accountId, from, to)
            .stream()
            .map(accountMapper::toInterestChargeResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public void postInterestCharge(UUID accountId, ChargeType chargeType, BigDecimal amount, String description) {
        log.info("Posting interest charge for account: {} of type: {} and amount: {}", accountId, chargeType, amount);

        InterestCharge charge = InterestCharge.builder()
            .id(UUID.randomUUID())
            .accountId(accountId)
            .chargeType(chargeType)
            .amount(amount)
            .description(description)
            .postDate(LocalDate.now())
            .effectiveDate(LocalDate.now())
            .build();

        InterestCharge savedCharge = interestChargeRepository.save(charge);

        log.info("Interest charge posted successfully: {}", savedCharge.getId());
    }
}
