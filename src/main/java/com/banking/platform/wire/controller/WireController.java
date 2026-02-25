package com.banking.platform.wire.controller;

import com.banking.platform.shared.util.PagedResponse;
import com.banking.platform.wire.model.dto.InitiateWireRequest;
import com.banking.platform.wire.model.dto.WireTransferResponse;
import com.banking.platform.wire.service.WireService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wire")
@RequiredArgsConstructor
@Slf4j
public class WireController {

    private final WireService wireService;

    @PostMapping("/transfers")
    public ResponseEntity<WireTransferResponse> initiateWire(@Valid @RequestBody InitiateWireRequest request) {
        log.info("API request: Initiating wire transfer for account: {}", request.accountId());
        WireTransferResponse response = wireService.initiateWire(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/transfers/{id}")
    public ResponseEntity<WireTransferResponse> getWire(@PathVariable UUID id) {
        log.info("API request: Retrieving wire transfer: {}", id);
        WireTransferResponse response = wireService.getWire(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transfers/reference/{ref}")
    public ResponseEntity<WireTransferResponse> getWireByReference(@PathVariable String ref) {
        log.info("API request: Retrieving wire transfer by reference: {}", ref);
        WireTransferResponse response = wireService.getByReference(ref);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transfers/account/{accountId}")
    public ResponseEntity<PagedResponse<WireTransferResponse>> listWires(
        @PathVariable UUID accountId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        log.info("API request: Listing wire transfers for account: {}, page: {}, size: {}", accountId, page, size);
        PagedResponse<WireTransferResponse> response = wireService.listWires(accountId, page, size);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfers/{id}/approve")
    public ResponseEntity<WireTransferResponse> approveWire(@PathVariable UUID id) {
        log.info("API request: Approving wire transfer: {}", id);
        WireTransferResponse response = wireService.approveWire(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfers/{id}/cancel")
    public ResponseEntity<WireTransferResponse> cancelWire(@PathVariable UUID id) {
        log.info("API request: Cancelling wire transfer: {}", id);
        WireTransferResponse response = wireService.cancelWire(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfers/{id}/complete")
    public ResponseEntity<WireTransferResponse> completeWire(
        @PathVariable UUID id,
        @RequestParam String fedReferenceNumber) {
        log.info("API request: Completing wire transfer: {} with fed reference: {}", id, fedReferenceNumber);
        WireTransferResponse response = wireService.completeWire(id, fedReferenceNumber);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfers/{id}/fail")
    public ResponseEntity<WireTransferResponse> failWire(
        @PathVariable UUID id,
        @RequestParam String reason) {
        log.info("API request: Failing wire transfer: {} with reason: {}", id, reason);
        WireTransferResponse response = wireService.failWire(id, reason);
        return ResponseEntity.ok(response);
    }
}
