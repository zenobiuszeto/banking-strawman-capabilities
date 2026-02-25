package com.banking.platform.onboarding.service;

import com.banking.platform.onboarding.mapper.ApplicationMapper;
import com.banking.platform.onboarding.model.dto.ApplicationResponse;
import com.banking.platform.onboarding.model.dto.ApplicationSummaryResponse;
import com.banking.platform.onboarding.model.dto.CreateApplicationRequest;
import com.banking.platform.onboarding.model.dto.UpdateApplicationStatusRequest;
import com.banking.platform.onboarding.model.dto.UploadDocumentRequest;
import com.banking.platform.onboarding.model.entity.Application;
import com.banking.platform.onboarding.model.entity.ApplicationDocument;
import com.banking.platform.onboarding.model.entity.ApplicationStatus;
import com.banking.platform.onboarding.model.event.ApplicationEvent;
import com.banking.platform.onboarding.repository.ApplicationDocumentRepository;
import com.banking.platform.onboarding.repository.ApplicationRepository;
import com.banking.platform.shared.exception.BusinessException;
import com.banking.platform.shared.exception.DuplicateResourceException;
import com.banking.platform.shared.exception.ResourceNotFoundException;
import com.banking.platform.shared.util.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationDocumentRepository applicationDocumentRepository;
    private final ApplicationMapper applicationMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String KAFKA_TOPIC = "banking.application.events";

    public ApplicationResponse submitApplication(CreateApplicationRequest request) {
        log.info("Submitting new application for email: {}", request.email());

        if (applicationRepository.findByEmail(request.email()).isPresent()) {
            log.warn("Duplicate email submission attempt: {}", request.email());
            throw new DuplicateResourceException("An application with this email already exists");
        }

        Application application = applicationMapper.toEntity(request);
        Application savedApplication = applicationRepository.save(application);

        log.info("Application submitted with id: {}", savedApplication.getId());

        ApplicationEvent event = ApplicationEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("application.submitted")
            .timestamp(Instant.now())
            .applicationId(savedApplication.getId())
            .status(savedApplication.getStatus())
            .accountType(savedApplication.getRequestedAccountType())
            .build();

        publishEvent(event);

        return applicationMapper.toResponse(savedApplication);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplication(UUID applicationId) {
        log.info("Fetching application with id: {}", applicationId);

        Application application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> {
                log.error("Application not found with id: {}", applicationId);
                return new ResourceNotFoundException("Application not found with id: " + applicationId);
            });

        return applicationMapper.toResponse(application);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ApplicationSummaryResponse> listApplications(
        ApplicationStatus status, int page, int size) {
        log.info("Listing applications with status: {}, page: {}, size: {}", status, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<Application> applicationPage = applicationRepository.findByStatus(status, pageable);

        List<ApplicationSummaryResponse> content = applicationPage.getContent().stream()
            .map(applicationMapper::toSummary)
            .toList();

        return new PagedResponse<>(
            content,
            applicationPage.getNumber(),
            applicationPage.getSize(),
            applicationPage.getTotalElements(),
            applicationPage.getTotalPages(),
            applicationPage.isLast()
        );
    }

    public ApplicationResponse updateStatus(UUID applicationId, UpdateApplicationStatusRequest request) {
        log.info("Updating application {} status to {}", applicationId, request.status());

        Application application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        validateStatusTransition(application.getStatus(), request.status());

        application.setStatus(request.status());
        application.setReviewedAt(Instant.now());
        application.setAssignedReviewerId(request.reviewerId());
        if (request.rejectionReason() != null) {
            application.setRejectionReason(request.rejectionReason());
        }

        Application updatedApplication = applicationRepository.save(application);

        log.info("Application {} status updated to {}", applicationId, request.status());

        String eventType = getEventType(request.status());
        ApplicationEvent event = ApplicationEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(eventType)
            .timestamp(Instant.now())
            .applicationId(updatedApplication.getId())
            .customerId(request.reviewerId())
            .status(updatedApplication.getStatus())
            .accountType(updatedApplication.getRequestedAccountType())
            .build();

        publishEvent(event);

        return applicationMapper.toResponse(updatedApplication);
    }

    public void uploadDocument(UUID applicationId, UploadDocumentRequest request) {
        log.info("Uploading document of type {} for application {}", request.type(), applicationId);

        Application application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        ApplicationDocument document = ApplicationDocument.builder()
            .applicationId(applicationId)
            .documentType(request.type())
            .fileName(request.fileName())
            .fileUrl(request.fileUrl())
            .mimeType(extractMimeType(request.fileName()))
            .build();

        applicationDocumentRepository.save(document);

        log.info("Document uploaded successfully for application {}", applicationId);
    }

    @Transactional(readOnly = true)
    public List<ApplicationDocument> getDocuments(UUID applicationId) {
        log.info("Fetching documents for application {}", applicationId);

        applicationRepository.findById(applicationId)
            .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        return applicationDocumentRepository.findByApplicationId(applicationId);
    }

    private void validateStatusTransition(ApplicationStatus currentStatus, ApplicationStatus newStatus) {
        boolean isValid = switch (currentStatus) {
            case SUBMITTED -> newStatus == ApplicationStatus.UNDER_REVIEW || newStatus == ApplicationStatus.REJECTED;
            case UNDER_REVIEW -> newStatus == ApplicationStatus.KYC_PENDING || newStatus == ApplicationStatus.REJECTED;
            case KYC_PENDING -> newStatus == ApplicationStatus.KYC_APPROVED || newStatus == ApplicationStatus.KYC_REJECTED;
            case KYC_APPROVED -> newStatus == ApplicationStatus.APPROVED;
            case KYC_REJECTED, REJECTED, APPROVED, ACCOUNT_CREATED -> false;
        };

        if (!isValid) {
            log.warn("Invalid status transition from {} to {}", currentStatus, newStatus);
            throw new BusinessException("Invalid status transition from " + currentStatus + " to " + newStatus);
        }
    }

    private String getEventType(ApplicationStatus status) {
        return switch (status) {
            case APPROVED, ACCOUNT_CREATED -> "application.approved";
            case REJECTED, KYC_REJECTED -> "application.rejected";
            default -> "application.status.updated";
        };
    }

    private String extractMimeType(String fileName) {
        if (fileName == null) {
            return "application/octet-stream";
        }
        if (fileName.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
            return "application/msword";
        }
        return "application/octet-stream";
    }

    private void publishEvent(ApplicationEvent event) {
        try {
            kafkaTemplate.send(KAFKA_TOPIC, event.getApplicationId().toString(), event);
            log.info("Event published: {} for application {}", event.getEventType(), event.getApplicationId());
        } catch (Exception e) {
            log.error("Failed to publish event for application {}", event.getApplicationId(), e);
        }
    }
}
