package com.banking.platform.ledger.repository;

import com.banking.platform.ledger.model.entity.JournalEntry;
import com.banking.platform.ledger.model.entity.JournalEntryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByEntryNumber(String entryNumber);

    Page<JournalEntry> findByStatusOrderByPostingDateDesc(JournalEntryStatus status, Pageable pageable);

    Page<JournalEntry> findByPostingDateBetweenOrderByPostingDateDesc(LocalDate start, LocalDate end, Pageable pageable);

    @Query("SELECT MAX(CAST(SUBSTRING(j.entryNumber, 4) AS long)) FROM JournalEntry j WHERE j.entryNumber LIKE :prefix%")
    Long findMaxEntryNumberByPrefix(String prefix);

    Page<JournalEntry> findByReferenceTypeAndReferenceIdOrderByPostingDateDesc(
            String referenceType, UUID referenceId, Pageable pageable);
}

