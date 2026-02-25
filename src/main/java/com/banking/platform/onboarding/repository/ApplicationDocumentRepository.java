package com.banking.platform.onboarding.repository;

import com.banking.platform.onboarding.model.entity.ApplicationDocument;
import com.banking.platform.onboarding.model.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, UUID> {

    List<ApplicationDocument> findByApplicationId(UUID applicationId);

    Optional<ApplicationDocument> findByApplicationIdAndDocumentType(UUID applicationId, DocumentType documentType);
}
