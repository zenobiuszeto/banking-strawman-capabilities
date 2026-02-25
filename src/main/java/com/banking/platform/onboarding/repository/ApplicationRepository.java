package com.banking.platform.onboarding.repository;

import com.banking.platform.onboarding.model.entity.Application;
import com.banking.platform.onboarding.model.entity.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    Optional<Application> findByEmail(String email);

    Page<Application> findByStatus(ApplicationStatus status, Pageable pageable);

    Page<Application> findByStatusIn(List<ApplicationStatus> statuses, Pageable pageable);

    @Query("SELECT a FROM Application a WHERE a.submittedAt BETWEEN :startDate AND :endDate")
    Page<Application> findBySubmittedDateRange(
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate,
        Pageable pageable
    );

    @Modifying
    @Transactional
    @Query("UPDATE Application a SET a.status = :status, a.updatedAt = :updatedAt WHERE a.id = :id")
    int updateStatus(
        @Param("id") UUID id,
        @Param("status") ApplicationStatus status,
        @Param("updatedAt") Instant updatedAt
    );
}
