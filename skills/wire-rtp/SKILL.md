---
name: wire-rtp
description: |
  **Wire Transfer & Real-Time Payment Processing**: Comprehensive implementation of domestic Fedwire, international SWIFT MT103/pacs.008, and real-time payment systems (RTP/FedNow) with end-to-end tracking, sanctions screening, and recall/investigation workflows.

  MANDATORY TRIGGERS: wire transfer, wire, Fedwire, SWIFT, SWIFT gpi, MT103, pacs.008, RTP, FedNow, real-time payment, real-time payments, WireTransferService, WireAdapter, FedwireAdapter, SwiftAdapter, RtpAdapter, FedNowAdapter, wire origination, wire receiving, IMAD, OMAD, wire confirmation, wire tracking, SWIFTRef, BIC code, IBAN, correspondent bank, wire cutoff, 5:00 PM ET, wire sanctions, OFAC wire, wire recall, wire investigation, gpi tracker, uetr, end-to-end transaction reference, wire status, wire posting, wire memo posting, wire final posting
---

# Wire Transfer & Real-Time Payment Processing

Wire transfers are the highest-value payment channel in banking, requiring strict compliance with Federal Reserve, SWIFT, and payment network rules. This skill covers domestic Fedwire origination and settlement via Fedline, international SWIFT MT103/pacs.008 messages with gpi tracking, real-time payment networks (TCH RTP, FedNow), mandatory OFAC sanctions screening before transmission, wire recall and investigation workflows, and state-machine-driven status tracking with Kafka event streaming for operational visibility and dispute resolution.

---

## WireTransferService: Core Orchestration

The WireTransferService is the primary API for wire origination, responsible for business logic validation, sanctions screening, routing to the appropriate adapter (Fedwire, SWIFT, RTP, FedNow), and state management.

```java
@Service
@Slf4j
public class WireTransferService {
    
    private final WireTransferRepository wireRepository;
    private final SanctionsCheckService sanctionsService;
    private final FedwireAdapter fedwireAdapter;
    private final SwiftAdapter swiftAdapter;
    private final RtpAdapter rtpAdapter;
    private final FedNowAdapter fedNowAdapter;
    private final WireStatusStateMachine statusStateMachine;
    private final KafkaTemplate<String, WireEvent> kafkaTemplate;
    private final WireRecallService recallService;
    
    @Autowired
    public WireTransferService(WireTransferRepository wireRepository,
                               SanctionsCheckService sanctionsService,
                               FedwireAdapter fedwireAdapter,
                               SwiftAdapter swiftAdapter,
                               RtpAdapter rtpAdapter,
                               FedNowAdapter fedNowAdapter,
                               WireStatusStateMachine statusStateMachine,
                               KafkaTemplate<String, WireEvent> kafkaTemplate,
                               WireRecallService recallService) {
        this.wireRepository = wireRepository;
        this.sanctionsService = sanctionsService;
        this.fedwireAdapter = fedwireAdapter;
        this.swiftAdapter = swiftAdapter;
        this.rtpAdapter = rtpAdapter;
        this.fedNowAdapter = fedNowAdapter;
        this.statusStateMachine = statusStateMachine;
        this.kafkaTemplate = kafkaTemplate;
        this.recallService = recallService;
    }
    
    @Transactional
    public WireTransferResponse originateWire(WireOriginationRequest request) {
        log.info("Processing wire transfer: amount={}, receiver={}", request.getAmount(), request.getReceiverName());
        
        // Validate wire amount and account
        validateWireRequest(request);
        
        // Create wire entity in INITIATED state
        WireTransfer wire = new WireTransfer();
        wire.setOriginatingAccountId(request.getOriginatingAccountId());
        wire.setAmount(request.getAmount());
        wire.setCurrency(request.getCurrency());
        wire.setReceiverName(request.getReceiverName());
        wire.setReceiverBankBic(request.getReceiverBankBic());
        wire.setReceiverIban(request.getReceiverIban());
        wire.setReceiverAccountNumber(request.getReceiverAccountNumber());
        wire.setNarrative(request.getNarrative());
        wire.setStatus(WireStatus.INITIATED);
        wire.setCreatedAt(LocalDateTime.now(ZoneId.of("America/New_York")));
        
        wire = wireRepository.save(wire);
        publishWireEvent(wire, "INITIATED", "Wire transfer created");
        
        // Perform sanctions screening on receiver
        SanctionsCheckResult sanctionsResult = sanctionsService.screenWireRecipient(
            wire.getId(),
            request.getReceiverName(),
            request.getReceiverBankBic()
        );
        
        if (sanctionsResult.isBlocked()) {
            wire.setStatus(WireStatus.SANCTIONS_REJECTED);
            wire.setRejectionReason("OFAC match - " + sanctionsResult.getMatchedEntity());
            wireRepository.save(wire);
            publishWireEvent(wire, "SANCTIONS_REJECTED", sanctionsResult.getMatchedEntity());
            throw new WireSanctionsException("Receiver is on OFAC SDN list: " + sanctionsResult.getMatchedEntity());
        }
        
        // Update status to SANCTIONS_CHECKED
        wire.setStatus(WireStatus.SANCTIONS_CHECKED);
        wireRepository.save(wire);
        publishWireEvent(wire, "SANCTIONS_CHECKED", "Sanctions screening passed");
        
        // Enforce wire cutoff time (5:00 PM ET)
        LocalTime currentTime = LocalTime.now(ZoneId.of("America/New_York"));
        LocalTime cutoffTime = LocalTime.of(17, 0, 0);
        boolean isDomestic = isWireDomestic(request);
        
        if (currentTime.isAfter(cutoffTime) && isDomestic) {
            wire.setStatus(WireStatus.HELD_FOR_NEXT_BUSINESS_DAY);
            wireRepository.save(wire);
            publishWireEvent(wire, "HELD_FOR_NEXT_BUSINESS_DAY", "Wire cutoff time exceeded");
            return new WireTransferResponse(wire.getId(), "Wire queued for next business day");
        }
        
        // Route to appropriate adapter based on wire type
        WireTransmissionResult transmissionResult;
        try {
            if (isDomestic) {
                transmissionResult = fedwireAdapter.transmitDomesticWire(wire);
                wire.setWireType("FEDWIRE");
                wire.setFedwireImad(transmissionResult.getImad());
            } else {
                // International wire
                if (request.useSwiftGpi()) {
                    transmissionResult = swiftAdapter.transmitSwiftMessage(wire);
                    wire.setWireType("SWIFT_GPI");
                    wire.setSwiftReference(transmissionResult.getSwiftRef());
                    wire.setUetr(transmissionResult.getUetr());
                } else {
                    transmissionResult = swiftAdapter.transmitSwiftMessage(wire);
                    wire.setWireType("SWIFT");
                    wire.setSwiftReference(transmissionResult.getSwiftRef());
                }
            }
        } catch (Exception e) {
            wire.setStatus(WireStatus.TRANSMISSION_FAILED);
            wire.setRejectionReason(e.getMessage());
            wireRepository.save(wire);
            publishWireEvent(wire, "TRANSMISSION_FAILED", e.getMessage());
            throw new WireTransmissionException("Failed to transmit wire", e);
        }
        
        wire.setStatus(WireStatus.SUBMITTED);
        wire.setSubmittedAt(LocalDateTime.now());
        wireRepository.save(wire);
        publishWireEvent(wire, "SUBMITTED", "Wire transmitted to network");
        
        return new WireTransferResponse(
            wire.getId(),
            "Wire transfer submitted successfully",
            transmissionResult.getConfirmationNumber()
        );
    }
    
    @Transactional
    public void handleWireConfirmation(String wireId, WireConfirmationEvent event) {
        WireTransfer wire = wireRepository.findById(wireId)
            .orElseThrow(() -> new WireNotFoundException("Wire not found: " + wireId));
        
        wire.setStatus(WireStatus.CONFIRMED);
        wire.setConfirmedAt(LocalDateTime.now());
        wire.setConfirmationNumber(event.getConfirmationNumber());
        wireRepository.save(wire);
        publishWireEvent(wire, "CONFIRMED", "Wire confirmed by network");
        
        // Trigger final posting to general ledger
        postWireToLedger(wire);
    }
    
    private void validateWireRequest(WireOriginationRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Wire amount must be positive");
        }
        if (request.getAmount().compareTo(new BigDecimal("1000000")) > 0 && request.getChannel() == WireChannel.RTP) {
            throw new IllegalArgumentException("RTP limit exceeded: $1,000,000 maximum");
        }
        if (request.getReceiverName() == null || request.getReceiverName().trim().isEmpty()) {
            throw new IllegalArgumentException("Receiver name is required");
        }
    }
    
    private boolean isWireDomestic(WireOriginationRequest request) {
        return request.getReceiverIban() == null || 
               request.getReceiverIban().isEmpty();
    }
    
    private void publishWireEvent(WireTransfer wire, String eventType, String description) {
        WireEvent event = WireEvent.builder()
            .wireId(wire.getId())
            .amount(wire.getAmount())
            .currency(wire.getCurrency())
            .status(wire.getStatus().toString())
            .eventType(eventType)
            .description(description)
            .timestamp(LocalDateTime.now())
            .build();
        
        kafkaTemplate.send("wire-events", wire.getId(), event);
    }
    
    private void postWireToLedger(WireTransfer wire) {
        log.info("Posting confirmed wire to general ledger: wireId={}, amount={}", wire.getId(), wire.getAmount());
        // Integration with GL posting service
    }
}
```

---

## FedwireAdapter: Domestic Wire Transmission

The FedwireAdapter handles the technical formatting and transmission of domestic Fedwire messages via Fedline.

```java
@Component
@Slf4j
public class FedwireAdapter {
    
    private final FedlineGateway fedlineGateway;
    
    @Autowired
    public FedwireAdapter(FedlineGateway fedlineGateway) {
        this.fedlineGateway = fedlineGateway;
    }
    
    public WireTransmissionResult transmitDomesticWire(WireTransfer wire) {
        log.info("Transmitting Fedwire message: wireId={}, amount={}", wire.getId(), wire.getAmount());
        
        // Build Fedwire message structure
        FedwireMessage message = new FedwireMessage();
        
        // Block 1500: Sender Information
        message.setSenderRoutingNumber("021000021"); // Example FRB routing number
        message.setSenderAccountNumber(wire.getOriginatingAccountId());
        
        // Block 2000: Amount and Type
        message.setFundsCode("03"); // Credit transfer
        message.setAmount(wire.getAmount());
        
        // Block 3100: Receiving Bank Information
        message.setReceivingDfiRoutingNumber(wire.getReceiverBankRoutingNumber());
        
        // Block 3400: Receiving Institution
        message.setReceivingBankName(wire.getReceiverBankName());
        
        // Block 3600: Beneficiary Information
        message.setBeneficiaryAccountNumber(wire.getReceiverAccountNumber());
        message.setBeneficiaryName(wire.getReceiverName());
        
        // Block 4200: Originator Information
        message.setOriginatorName("ACME BANK");
        message.setOriginatorRoutingNumber("021000021");
        
        // Optional: 6000 series for narrative/reference
        if (wire.getNarrative() != null && !wire.getNarrative().isEmpty()) {
            message.setNarrative(wire.getNarrative().substring(0, Math.min(140, wire.getNarrative().length())));
        }
        
        // Transmit to Fedline gateway
        FedlineResponse response = fedlineGateway.send(message);
        
        if (!response.isSuccessful()) {
            throw new FedlineTransmissionException("Fedwire transmission failed: " + response.getErrorMessage());
        }
        
        log.info("Fedwire transmitted successfully: IMAD={}", response.getImad());
        
        return WireTransmissionResult.builder()
            .confirmationNumber(response.getConfirmationNumber())
            .imad(response.getImad())
            .omad(null)
            .build();
    }
}
```

---

## SwiftAdapter: International Wire & SWIFT GPI

The SwiftAdapter formats and sends international SWIFT MT103 messages with optional SWIFT gpi tracking for real-time end-to-end visibility.

```java
@Component
@Slf4j
public class SwiftAdapter {
    
    private final SwiftGateway swiftGateway;
    private final SwiftGpiTracker gpiTracker;
    
    @Autowired
    public SwiftAdapter(SwiftGateway swiftGateway, SwiftGpiTracker gpiTracker) {
        this.swiftGateway = swiftGateway;
        this.gpiTracker = gpiTracker;
    }
    
    public WireTransmissionResult transmitSwiftMessage(WireTransfer wire) {
        log.info("Transmitting SWIFT MT103 message: wireId={}, receiverBic={}", wire.getId(), wire.getReceiverBankBic());
        
        // Generate UETR (Unique End-to-End Transaction Reference) for SWIFT gpi
        String uetr = generateUetr();
        
        // Build MT103 message
        SwiftMt103Message mt103 = new SwiftMt103Message();
        
        // Field 20: Transaction Reference
        mt103.setTransactionReference(generateSwiftReference());
        
        // Field 23B: Bank Operation Code
        mt103.setBankOperationCode("CRED");
        
        // Field 32A: Value Date and Currency Code
        mt103.setValueDate(LocalDate.now());
        mt103.setCurrencyCode("USD");
        mt103.setAmount(wire.getAmount());
        
        // Field 50K: Originator
        mt103.setOriginatorName("ACME BANK");
        mt103.setOriginatorIban("US12345678901234567890");
        
        // Field 59: Beneficiary Account
        mt103.setBeneficiaryAccountNumber(wire.getReceiverIban());
        
        // Field 57A: Receiving Bank (BIC)
        mt103.setReceivingBankBic(wire.getReceiverBankBic());
        
        // Field 59A: Beneficiary Name and Address
        mt103.setBeneficiaryName(wire.getReceiverName());
        
        // Field 70: Details of Payment
        String narrative = wire.getNarrative();
        if (narrative != null && !narrative.isEmpty()) {
            mt103.setPaymentDetails(narrative.substring(0, Math.min(140, narrative.length())));
        }
        
        // Field 71A: Details of Charges
        mt103.setChargesCode("SHA"); // Shared charges
        
        // SWIFT gpi enhancements
        if (wire.isUseSwiftGpi()) {
            mt103.setUetr(uetr);
            mt103.setPriorityIndicator("U"); // Urgent
        }
        
        // Transmit to SWIFT network
        SwiftResponse response = swiftGateway.send(mt103);
        
        if (!response.isSuccessful()) {
            throw new SwiftTransmissionException("SWIFT transmission failed: " + response.getErrorMessage());
        }
        
        log.info("SWIFT MT103 transmitted: SWIFT_REF={}, UETR={}", response.getSwiftReference(), uetr);
        
        // Optionally register for gpi tracking
        if (wire.isUseSwiftGpi()) {
            gpiTracker.registerTransaction(uetr, wire.getId(), wire.getReceiverBankBic());
        }
        
        return WireTransmissionResult.builder()
            .confirmationNumber(response.getConfirmationNumber())
            .swiftRef(response.getSwiftReference())
            .uetr(uetr)
            .build();
    }
    
    private String generateUetr() {
        // UETR format: 4 char bank code + 20 digits + 4 char check
        return UUID.randomUUID().toString().replace("-", "").substring(0, 32).toUpperCase();
    }
    
    private String generateSwiftReference() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")) + 
               UUID.randomUUID().toString().substring(0, 10).toUpperCase();
    }
}
```

---

## RtpAdapter: Real-Time Payment via TCH

The RtpAdapter handles ISO 20022 pacs.008 messages for TCH RTP, delivering funds in seconds with full RTGS functionality.

```java
@Component
@Slf4j
public class RtpAdapter {
    
    private final RtpGateway rtpGateway;
    private final Pacs008Builder pacs008Builder;
    
    @Autowired
    public RtpAdapter(RtpGateway rtpGateway, Pacs008Builder pacs008Builder) {
        this.rtpGateway = rtpGateway;
        this.pacs008Builder = pacs008Builder;
    }
    
    public WireTransmissionResult transmitRtpPayment(WireTransfer wire) {
        log.info("Transmitting RTP payment: wireId={}, amount={}", wire.getId(), wire.getAmount());
        
        // Validate RTP eligibility
        if (wire.getAmount().compareTo(new BigDecimal("1000000")) > 0) {
            throw new RtpLimitExceededException("RTP limit: $1,000,000 maximum");
        }
        
        // Build ISO 20022 pacs.008 message
        String pacs008Xml = pacs008Builder.buildRtpMessage(
            wire.getId(),
            wire.getAmount(),
            wire.getCurrency(),
            wire.getOriginatingAccountId(),
            wire.getReceiverAccountNumber(),
            wire.getReceiverName()
        );
        
        // Transmit to RTP network
        RtpResponse response = rtpGateway.sendPacs008(pacs008Xml);
        
        if (!response.isSuccessful()) {
            throw new RtpTransmissionException("RTP transmission failed: " + response.getErrorMessage());
        }
        
        log.info("RTP payment transmitted: transactionId={}, status=accepted", response.getTransactionId());
        
        return WireTransmissionResult.builder()
            .confirmationNumber(response.getTransactionId())
            .rtpConfirmationTime(LocalDateTime.now())
            .rtpConfirmed(true)
            .build();
    }
}

@Component
class Pacs008Builder {
    
    public String buildRtpMessage(String wireId, BigDecimal amount, String currency,
                                  String senderAccount, String receiverAccount, String receiverName) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.003.02\">\n");
        xml.append("  <CstmrCdtTrfInitn>\n");
        xml.append("    <GrpHdr>\n");
        xml.append("      <MsgId>").append(wireId).append("</MsgId>\n");
        xml.append("      <CreDtTm>").append(LocalDateTime.now()).append("</CreDtTm>\n");
        xml.append("      <NbOfTxns>1</NbOfTxns>\n");
        xml.append("      <CtrlSum>").append(amount).append("</CtrlSum>\n");
        xml.append("    </GrpHdr>\n");
        xml.append("    <PmtInf>\n");
        xml.append("      <PmtMtd>TRF</PmtMtd>\n");
        xml.append("      <PmtTpInf>\n");
        xml.append("        <LclInstrm>\n");
        xml.append("          <Prtry>RTP</Prtry>\n");
        xml.append("        </LclInstrm>\n");
        xml.append("      </PmtTpInf>\n");
        xml.append("      <CdtTrfTxInf>\n");
        xml.append("        <PmtId>\n");
        xml.append("          <InstrId>").append(wireId).append("</InstrId>\n");
        xml.append("        </PmtId>\n");
        xml.append("        <Amt>\n");
        xml.append("          <InstdAmt Ccy=\"").append(currency).append("\">").append(amount).append("</InstdAmt>\n");
        xml.append("        </Amt>\n");
        xml.append("        <DbtrAcct>\n");
        xml.append("          <Id>\n");
        xml.append("            <Othr>\n");
        xml.append("              <Id>").append(senderAccount).append("</Id>\n");
        xml.append("            </Othr>\n");
        xml.append("          </Id>\n");
        xml.append("        </DbtrAcct>\n");
        xml.append("        <CdtrAcct>\n");
        xml.append("          <Id>\n");
        xml.append("            <Othr>\n");
        xml.append("              <Id>").append(receiverAccount).append("</Id>\n");
        xml.append("            </Othr>\n");
        xml.append("          </Id>\n");
        xml.append("        </CdtrAcct>\n");
        xml.append("        <Cdtr>\n");
        xml.append("          <Nm>").append(receiverName).append("</Nm>\n");
        xml.append("        </Cdtr>\n");
        xml.append("      </CdtTrfTxInf>\n");
        xml.append("    </PmtInf>\n");
        xml.append("  </CstmrCdtTrfInitn>\n");
        xml.append("</Document>\n");
        
        return xml.toString();
    }
}
```

---

## SanctionsCheckService: OFAC Compliance

The SanctionsCheckService screens wire recipients against OFAC SDN list before any transmission, blocking sanctioned entities.

```java
@Service
@Slf4j
public class SanctionsCheckService {
    
    private final OfacSdnRepository sdnRepository;
    private final OfacListSyncService listSyncService;
    
    @Autowired
    public SanctionsCheckService(OfacSdnRepository sdnRepository, OfacListSyncService listSyncService) {
        this.sdnRepository = sdnRepository;
        this.listSyncService = listSyncService;
    }
    
    public SanctionsCheckResult screenWireRecipient(String wireId, String recipientName, String bicCode) {
        log.info("Screening wire recipient: wireId={}, name={}, bic={}", wireId, recipientName, bicCode);
        
        // Exact match on recipient name
        List<OfacSdnEntity> exactMatches = sdnRepository.findByNameIgnoreCase(recipientName);
        if (!exactMatches.isEmpty()) {
            OfacSdnEntity match = exactMatches.get(0);
            log.warn("EXACT match on OFAC SDN list: {} - BLOCKING", match.getName());
            return new SanctionsCheckResult(true, match.getName(), "EXACT_MATCH");
        }
        
        // Fuzzy match using Levenshtein distance
        List<OfacSdnEntity> allEntities = sdnRepository.findAll();
        for (OfacSdnEntity entity : allEntities) {
            int distance = levenshteinDistance(recipientName.toUpperCase(), entity.getName().toUpperCase());
            if (distance <= 2 && distance > 0) {
                log.warn("FUZZY match on OFAC SDN list: {} (distance={})", entity.getName(), distance);
                return new SanctionsCheckResult(true, entity.getName(), "FUZZY_MATCH");
            }
        }
        
        // Check BIC code against bank sanctions list
        if (bicCode != null && !bicCode.isEmpty()) {
            List<OfacSdnEntity> bicMatches = sdnRepository.findByAliasContainingIgnoreCase(bicCode);
            if (!bicMatches.isEmpty()) {
                log.warn("BIC match on OFAC list: {} - BLOCKING", bicCode);
                return new SanctionsCheckResult(true, bicCode, "BIC_MATCH");
            }
        }
        
        log.info("Sanctions screening passed: wireId={}", wireId);
        return new SanctionsCheckResult(false, null, "PASS");
    }
    
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[s1.length()][s2.length()];
    }
}

@Data
class SanctionsCheckResult {
    private final boolean blocked;
    private final String matchedEntity;
    private final String matchType; // EXACT_MATCH, FUZZY_MATCH, BIC_MATCH, PASS
}
```

---

## WireStatusStateMachine: State Management

The WireStatusStateMachine enforces valid state transitions for wire lifecycle.

```java
@Component
@Slf4j
public class WireStatusStateMachine {
    
    private static final Map<WireStatus, Set<WireStatus>> VALID_TRANSITIONS = new HashMap<>();
    
    static {
        VALID_TRANSITIONS.put(WireStatus.INITIATED, Set.of(
            WireStatus.SANCTIONS_CHECKED, 
            WireStatus.SANCTIONS_REJECTED,
            WireStatus.HELD_FOR_NEXT_BUSINESS_DAY
        ));
        VALID_TRANSITIONS.put(WireStatus.SANCTIONS_CHECKED, Set.of(
            WireStatus.SUBMITTED,
            WireStatus.TRANSMISSION_FAILED
        ));
        VALID_TRANSITIONS.put(WireStatus.SUBMITTED, Set.of(
            WireStatus.CONFIRMED,
            WireStatus.REJECTED
        ));
        VALID_TRANSITIONS.put(WireStatus.CONFIRMED, Set.of(
            WireStatus.POSTED
        ));
        VALID_TRANSITIONS.put(WireStatus.HELD_FOR_NEXT_BUSINESS_DAY, Set.of(
            WireStatus.SANCTIONS_CHECKED
        ));
    }
    
    public void validateTransition(WireStatus currentStatus, WireStatus newStatus) {
        Set<WireStatus> allowedTransitions = VALID_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
        if (!allowedTransitions.contains(newStatus)) {
            throw new InvalidWireStateTransitionException(
                String.format("Invalid transition: %s -> %s", currentStatus, newStatus)
            );
        }
    }
}

enum WireStatus {
    INITIATED, SANCTIONS_CHECKED, SANCTIONS_REJECTED, HELD_FOR_NEXT_BUSINESS_DAY,
    SUBMITTED, CONFIRMED, REJECTED, POSTED, TRANSMISSION_FAILED
}
```

---

## WireTransfer Entity

The WireTransfer JPA entity models the wire transfer domain.

```java
@Entity
@Table(name = "wire_transfers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WireTransfer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String originatingAccountId;
    
    @Column(nullable = false)
    private BigDecimal amount;
    
    @Column(nullable = false, length = 3)
    private String currency;
    
    @Column(nullable = false)
    private String receiverName;
    
    private String receiverBankBic;
    private String receiverBankName;
    private String receiverIban;
    private String receiverAccountNumber;
    private String receiverBankRoutingNumber;
    
    @Column(length = 500)
    private String narrative;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WireStatus status;
    
    @Column(length = 20)
    private String wireType; // FEDWIRE, SWIFT, SWIFT_GPI, RTP, FEDNOW
    
    private String fedwireImad;
    private String fedwireOmad;
    private String swiftReference;
    private String uetr; // SWIFT gpi tracking
    private String confirmationNumber;
    
    @Column(length = 500)
    private String rejectionReason;
    
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime postedAt;
    
    private boolean useSwiftGpi;
}
```

---

## 10 Critical Rules

1. **Sanctions Screening is Mandatory**: Every wire transfer MUST be screened against OFAC SDN list before transmission. Block any exact or fuzzy match (Levenshtein distance <= 2). Maintain audit trail of all sanctions checks.

2. **Wire Cutoff Enforcement**: Enforce 5:00 PM ET cutoff for domestic Fedwire daily. Hold wires submitted after cutoff for next business day transmission. Exclude holidays and weekends from processing.

3. **Idempotent Transmission**: Wire adapters MUST be idempotent. Use message reference numbers (IMAD, SWIFT Ref, UETR) to prevent duplicate submissions. Implement request deduplication in all network gateways.

4. **State Machine Enforcement**: All wire status transitions MUST follow the defined state machine. Reject invalid transitions. Document all state changes with timestamp and reason in Kafka events.

5. **Fedline Security**: Fedline connections MUST use TLS 1.2 minimum, client certificate authentication, and end-to-end encryption. Implement connection pooling with auto-reconnect. Rotate credentials per Federal Reserve requirements.

6. **SWIFT GPI Tracking**: When SWIFT gpi is requested, assign UETR immediately. Register transaction with gpi tracker for real-time visibility. Provide public-facing status API using UETR lookup.

7. **RTP $1M Limit**: RTP channel MUST enforce $1,000,000 per-transaction limit. Reject any RTP wire exceeding limit. Route over-limit wires to Fedwire or SWIFT automatically.

8. **Account Verification**: Before wire origination, verify sending account is funded with available balance >= wire amount + applicable fees. Enforce account status is ACTIVE and not flagged for fraud/AML holds.

9. **Concurrent Wire Limits**: Enforce per-originator per-day limits on wire count and total amount (e.g., max 100 wires per day, $10M per day). Queue excess wires for manager approval with SLA tracking.

10. **Ledger Posting Synchronization**: Wire MUST transition to POSTED status only after successful GL posting of debit to originating account and credit to receiving account. Implement dual-ledger posting with reconciliation check.

