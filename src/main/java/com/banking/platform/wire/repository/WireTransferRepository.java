package com.banking.platform.wire.repository;

import com.banking.platform.wire.model.entity.WireStatus;
import com.banking.platform.wire.model.entity.WireTransfer;
import com.banking.platform.wire.model.entity.WireType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WireTransferRepository extends JpaRepository<WireTransfer, UUID> {

    Page<WireTransfer> findByAccountId(UUID accountId, Pageable pageable);

    Optional<WireTransfer> findByWireReferenceNumber(String wireReferenceNumber);

    List<WireTransfer> findByStatus(WireStatus status);

    List<WireTransfer> findByAccountIdAndCreatedAtBetween(UUID accountId, Instant fromDate, Instant toDate);

    List<WireTransfer> findByAccountIdAndWireType(UUID accountId, WireType wireType);

    List<WireTransfer> findByAccountIdAndStatus(UUID accountId, WireStatus status);
}
