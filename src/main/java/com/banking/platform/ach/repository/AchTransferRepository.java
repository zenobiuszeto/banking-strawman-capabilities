package com.banking.platform.ach.repository;

import com.banking.platform.ach.model.entity.AchStatus;
import com.banking.platform.ach.model.entity.AchTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ACH transfer data access operations.
 */
@Repository
public interface AchTransferRepository extends JpaRepository<AchTransfer, UUID> {

    /**
     * Find all ACH transfers for a given account, ordered by creation date descending.
     *
     * @param accountId the account ID
     * @param pageable pagination information
     * @return page of ACH transfers
     */
    Page<AchTransfer> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    /**
     * Find an ACH transfer by its unique trace number.
     *
     * @param traceNumber the trace number
     * @return optional containing the transfer if found
     */
    Optional<AchTransfer> findByTraceNumber(String traceNumber);

    /**
     * Find all ACH transfers with any of the specified statuses.
     *
     * @param statuses list of statuses to filter by
     * @return list of matching transfers
     */
    List<AchTransfer> findByStatusIn(List<AchStatus> statuses);

    /**
     * Find all ACH transfers for an account within a date range.
     *
     * @param accountId the account ID
     * @param startTime start of the date range
     * @param endTime end of the date range
     * @return list of matching transfers
     */
    List<AchTransfer> findByAccountIdAndCreatedAtBetween(UUID accountId, Instant startTime, Instant endTime);

    /**
     * Update the status of an ACH transfer.
     *
     * @param id the transfer ID
     * @param status the new status
     */
    @Modifying
    @Query("UPDATE AchTransfer a SET a.status = :status, a.updatedAt = CURRENT_TIMESTAMP WHERE a.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") AchStatus status);

    /**
     * Find ACH transfers by account ID and status.
     *
     * @param accountId the account ID
     * @param status the status
     * @return list of matching transfers
     */
    List<AchTransfer> findByAccountIdAndStatus(UUID accountId, AchStatus status);

    /**
     * Count ACH transfers by status for reporting and monitoring.
     *
     * @param status the status to count
     * @return count of transfers with that status
     */
    long countByStatus(AchStatus status);

    /**
     * Find all ACH transfers that need processing (status is APPROVED and ready for submission).
     *
     * @return list of transfers ready for processing
     */
    List<AchTransfer> findByStatus(AchStatus status);

}
