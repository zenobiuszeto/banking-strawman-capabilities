package com.banking.platform.onboarding.controller;

import com.banking.platform.onboarding.model.dto.ApplicationResponse;
import com.banking.platform.onboarding.model.dto.ApplicationSummaryResponse;
import com.banking.platform.onboarding.model.dto.CreateApplicationRequest;
import com.banking.platform.onboarding.model.dto.UpdateApplicationStatusRequest;
import com.banking.platform.onboarding.model.dto.UploadDocumentRequest;
import com.banking.platform.onboarding.model.entity.ApplicationDocument;
import com.banking.platform.onboarding.model.entity.ApplicationStatus;
import com.banking.platform.onboarding.service.ApplicationService;
import com.banking.platform.shared.util.PagedResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/onboarding/applications")
@Validated
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Onboarding")
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    public ResponseEntity<ApplicationResponse> submitApplication(
        @Valid @RequestBody CreateApplicationRequest request) {
        log.info("POST /api/v1/onboarding/applications - Submit application for email: {}", request.email());
        ApplicationResponse response = applicationService.submitApplication(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getApplication(@PathVariable UUID id) {
        log.info("GET /api/v1/onboarding/applications/{} - Fetch application", id);
        ApplicationResponse response = applicationService.getApplication(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ApplicationSummaryResponse>> listApplications(
        @RequestParam(defaultValue = "SUBMITTED") ApplicationStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/v1/onboarding/applications - List applications with status: {}, page: {}, size: {}",
                 status, page, size);
        PagedResponse<ApplicationSummaryResponse> response = applicationService.listApplications(status, page, size);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationResponse> updateStatus(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateApplicationStatusRequest request) {
        log.info("PATCH /api/v1/onboarding/applications/{}/status - Update status to: {}", id, request.status());
        ApplicationResponse response = applicationService.updateStatus(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/documents")
    public ResponseEntity<Void> uploadDocument(
        @PathVariable UUID id,
        @Valid @RequestBody UploadDocumentRequest request) {
        log.info("POST /api/v1/onboarding/applications/{}/documents - Upload document of type: {}", id, request.type());
        applicationService.uploadDocument(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{id}/documents")
    public ResponseEntity<List<ApplicationDocument>> getDocuments(@PathVariable UUID id) {
        log.info("GET /api/v1/onboarding/applications/{}/documents - Fetch documents", id);
        List<ApplicationDocument> documents = applicationService.getDocuments(id);
        return ResponseEntity.ok(documents);
    }
}
