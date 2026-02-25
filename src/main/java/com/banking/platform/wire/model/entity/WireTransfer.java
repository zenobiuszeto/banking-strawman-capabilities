package com.banking.platform.wire.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wire_transfers", indexes = {
    @Index(name = "idx_account_id", columnList = "account_id"),
    @Index(name = "idx_wire_reference", columnList = "wire_reference_number", unique = true),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WireTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "wire_reference_number", nullable = false, unique = true, length = 50)
    private String wireReferenceNumber;

    @Column(name = "fed_reference_number", length = 50)
    private String fedReferenceNumber;

    @Column(name = "wire_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private WireType wireType;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private WireStatus status;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal fee;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "sender_name", nullable = false, length = 100)
    private String senderName;

    @Column(name = "sender_account_number", nullable = false, length = 50)
    private String senderAccountNumber;

    @Column(name = "sender_routing_number", nullable = false, length = 20)
    private String senderRoutingNumber;

    @Column(name = "sender_bank_name", nullable = false, length = 100)
    private String senderBankName;

    @Column(name = "beneficiary_name", nullable = false, length = 100)
    private String beneficiaryName;

    @Column(name = "beneficiary_account_number", nullable = false, length = 50)
    private String beneficiaryAccountNumber;

    @Column(name = "beneficiary_routing_number", length = 20)
    private String beneficiaryRoutingNumber;

    @Column(name = "beneficiary_bank_name", nullable = false, length = 100)
    private String beneficiaryBankName;

    @Column(name = "beneficiary_bank_address", length = 200)
    private String beneficiaryBankAddress;

    @Column(name = "intermediary_bank_name", length = 100)
    private String intermediaryBankName;

    @Column(name = "intermediary_swift_code", length = 11)
    private String intermediarySwiftCode;

    @Column(name = "beneficiary_swift_code", length = 11)
    private String beneficiarySwiftCode;

    @Column(name = "beneficiary_iban", length = 34)
    private String beneficiaryIban;

    @Column(name = "purpose_of_wire", length = 200)
    private String purposeOfWire;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
