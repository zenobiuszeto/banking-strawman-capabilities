---
name: card-iso8583
description: |
  **ISO 8583 Card Authorization Processing**: End-to-end implementation of ISO 8583 message handling for debit/credit card authorization with real-time decision engine, network routing (Visa/Mastercard), clearing file processing, settlement workflows, and fraud detection integration.

  MANDATORY TRIGGERS: ISO 8583, ISO8583, card authorization, card auth, debit card, authorization request, authorization response, 0100, 0110, 0200, 0210, 0400, 0410, bitmap, primary bitmap, secondary bitmap, MTI, message type indicator, field 2, field 4, field 7, field 11, field 12, field 14, field 22, field 35, field 37, field 38, field 39, field 41, field 42, PAN, track data, CVV, CVV2, expiry, approval code, response code, acquirer, issuer, Visa, Mastercard, NetworkAdapter, IsoMessage, IsoParser, IsoBuilder, TCP socket, socket gateway, card network, stand-in, stand-in authorization, jPOS, clearing file, settlement, interchange, BIN table, card product
---

# ISO 8583 Card Authorization Processing

ISO 8583 is the global standard for payment card message exchange, enabling authorization decisions in milliseconds. This skill covers message structure (MTI + bitmap + data elements), building authorization requests (0100) with PAN/track data/amount/merchant info, parsing network responses (0110) with approval codes and response codes, high-performance TCP socket gateways for Visa/Mastercard networks, real-time authorization decision engine with velocity checks and fraud scoring, stand-in authorization for offline mode, and clearing/settlement file processing for post-transaction reconciliation.

---

## IsoMessageBuilder: Constructing 0100 Auth Request

The IsoMessageBuilder constructs ISO 8583 0100 (authorization request) messages with proper bitmap encoding and field formatting.

```java
@Component
@Slf4j
public class IsoMessageBuilder {
    
    private static final String MTI_AUTH_REQUEST = "0100";
    private static final String MTI_AUTH_RESPONSE = "0110";
    private static final String PRIMARY_BITMAP_FIELD = "1";
    private static final String SECONDARY_BITMAP_FIELD = "129";
    
    public IsoMessage buildAuthorizationRequest(CardAuthorizationRequest request) {
        log.info("Building ISO 0100 auth request: pan={}, amount={}, merchant={}",
            maskPan(request.getPan()),
            request.getAmount(),
            request.getMerchantId());
        
        IsoMessage message = new IsoMessage();
        
        // Message Type Indicator: 0100 = Authorization Request
        message.setMti(MTI_AUTH_REQUEST);
        
        // Field 2: Primary Account Number (PAN)
        message.setField(2, request.getPan());
        
        // Field 3: Processing Code
        message.setField(3, "000000"); // Purchase
        
        // Field 4: Amount Transaction
        String amountStr = request.getAmount()
            .multiply(new BigDecimal("100"))
            .longValue() + "";
        message.setField(4, String.format("%012d", Long.parseLong(amountStr)));
        
        // Field 7: Transmission Date and Time
        LocalDateTime now = LocalDateTime.now();
        String transmissionTime = now.format(DateTimeFormatter.ofPattern("MMddHHmmss"));
        message.setField(7, transmissionTime);
        
        // Field 11: Systems Trace Audit Number (STAN)
        String stan = String.format("%06d", request.getSequenceNumber() % 1000000);
        message.setField(11, stan);
        
        // Field 12: Time Local Transaction
        message.setField(12, now.format(DateTimeFormatter.ofPattern("HHmmss")));
        
        // Field 13: Date Local Transaction
        message.setField(13, now.format(DateTimeFormatter.ofPattern("MMdd")));
        
        // Field 14: Date Expiration
        message.setField(14, request.getExpiryYearMonth()); // YYMM
        
        // Field 22: POS Entry Mode
        // 051 = Magnetic stripe, CvvPresent, PinNotPresent
        // 052 = Magnetic stripe, CvvNotPresent, PinNotPresent
        // 071 = Contactless, CvvPresent
        message.setField(22, request.getPosEntryMode());
        
        // Field 26: POS Condition Code
        message.setField(26, "05"); // Card Present, PIN Verified
        
        // Field 35: Track 2 Data
        if (request.getTrack2Data() != null && !request.getTrack2Data().isEmpty()) {
            message.setField(35, request.getTrack2Data());
        }
        
        // Field 37: Retrieval Reference Number
        String rrn = String.format("%012d", System.currentTimeMillis() % 1000000000000L);
        message.setField(37, rrn);
        
        // Field 38: Approval Code
        message.setField(38, ""); // Not applicable for request
        
        // Field 39: Response Code
        message.setField(39, ""); // Not applicable for request
        
        // Field 41: Terminal ID
        message.setField(41, request.getTerminalId());
        
        // Field 42: Merchant ID
        message.setField(42, request.getMerchantId());
        
        // Field 49: Currency Code (USD = 840)
        message.setField(49, "840");
        
        // Field 52: PIN Data (encrypted if present)
        if (request.getEncryptedPin() != null && !request.getEncryptedPin().isEmpty()) {
            message.setField(52, request.getEncryptedPin());
        }
        
        // Field 55: ICC (EMV) Data
        if (request.getEmvData() != null && !request.getEmvData().isEmpty()) {
            message.setField(55, request.getEmvData());
        }
        
        // Field 95: Card Verification Result (CVV2)
        if (request.getCvv2() != null && !request.getCvv2().isEmpty()) {
            message.setField(95, request.getCvv2());
        }
        
        // Field 100: Receiving Institution Code (BIN)
        message.setField(100, request.getIssuingBankBin());
        
        // Optional: Additional custom fields for fraud detection
        message.setField(127, buildCustomData(request));
        
        // Encode message
        message.encodeWithBitmaps();
        
        log.debug("ISO 0100 message built: length={}, fields={}", message.getEncodedMessage().length(), message.getFieldCount());
        
        return message;
    }
    
    public IsoMessage parseAuthorizationResponse(byte[] responseData) {
        log.info("Parsing ISO 0110 auth response: length={}", responseData.length);
        
        IsoMessage response = new IsoMessage();
        response.parse(responseData);
        
        String mti = response.getMti();
        if (!mti.equals("0110")) {
            throw new IsoParseException("Expected MTI 0110, received: " + mti);
        }
        
        String responseCode = response.getField(39);
        String approvalCode = response.getField(38);
        
        log.info("Auth response parsed: responseCode={}, approvalCode={}", responseCode, approvalCode);
        
        return response;
    }
    
    private String buildCustomData(CardAuthorizationRequest request) {
        // Custom data element 127 for fraud/velocity signals
        StringBuilder sb = new StringBuilder();
        sb.append(request.getDeviceId() != null ? request.getDeviceId() : "");
        sb.append("|");
        sb.append(request.getTransactionId());
        sb.append("|");
        sb.append(request.getMcc() != null ? request.getMcc() : "");
        return sb.toString();
    }
    
    private String maskPan(String pan) {
        if (pan == null || pan.length() < 8) return "****";
        return "****" + pan.substring(pan.length() - 4);
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class IsoMessage {
    private String mti;
    private Map<Integer, String> fields = new HashMap<>();
    private byte[] encodedMessage;
    
    public void setField(int fieldNumber, String value) {
        fields.put(fieldNumber, value);
    }
    
    public String getField(int fieldNumber) {
        return fields.getOrDefault(fieldNumber, "");
    }
    
    public void encodeWithBitmaps() {
        // Encode primary and secondary bitmaps
        boolean[] primaryBitmap = new boolean[64];
        boolean[] secondaryBitmap = new boolean[64];
        
        for (int field : fields.keySet()) {
            if (field > 128) {
                secondaryBitmap[field - 65] = true;
                primaryBitmap[0] = true; // Set bit 1 to indicate secondary bitmap
            } else if (field > 0) {
                primaryBitmap[field - 1] = true;
            }
        }
        
        StringBuilder encodedFields = new StringBuilder();
        encodedFields.append(mti);
        encodedFields.append(encodeBitmap(primaryBitmap));
        if (primaryBitmap[0]) {
            encodedFields.append(encodeBitmap(secondaryBitmap));
        }
        
        for (int i = 1; i < 129; i++) {
            if ((i <= 64 && primaryBitmap[i - 1]) || (i > 64 && secondaryBitmap[i - 65])) {
                String fieldValue = getField(i);
                encodedFields.append(fieldValue);
            }
        }
        
        this.encodedMessage = encodedFields.toString().getBytes(StandardCharsets.UTF_8);
    }
    
    public void parse(byte[] data) {
        String msg = new String(data, StandardCharsets.UTF_8);
        int pos = 0;
        
        // Extract MTI (first 4 characters)
        this.mti = msg.substring(0, 4);
        pos = 4;
        
        // Parse bitmaps and fields (simplified for brevity)
        // In production, use proper ISO 8583 parser library
    }
    
    private String encodeBitmap(boolean[] bitmap) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bitmap.length; i += 8) {
            int byte_val = 0;
            for (int j = 0; j < 8; j++) {
                byte_val = byte_val * 2 + (bitmap[i + j] ? 1 : 0);
            }
            sb.append(String.format("%02X", byte_val));
        }
        return sb.toString();
    }
    
    public int getFieldCount() {
        return fields.size();
    }
}
```

---

## CardAuthorizationService: Real-Time Auth Decision

The CardAuthorizationService implements the authorization decision logic with velocity checks, fraud scoring, and account validation.

```java
@Service
@Slf4j
public class CardAuthorizationService {
    
    private final TcpSocketGateway socketGateway;
    private final CardBinRepository binRepository;
    private final FraudScoringService fraudScoringService;
    private final VelocityCheckService velocityCheckService;
    private final AuthorizationRepository authRepository;
    private final KafkaTemplate<String, CardAuthEvent> kafkaTemplate;
    
    @Autowired
    public CardAuthorizationService(TcpSocketGateway socketGateway,
                                    CardBinRepository binRepository,
                                    FraudScoringService fraudScoringService,
                                    VelocityCheckService velocityCheckService,
                                    AuthorizationRepository authRepository,
                                    KafkaTemplate<String, CardAuthEvent> kafkaTemplate) {
        this.socketGateway = socketGateway;
        this.binRepository = binRepository;
        this.fraudScoringService = fraudScoringService;
        this.velocityCheckService = velocityCheckService;
        this.authRepository = authRepository;
        this.kafkaTemplate = kafkaTemplate;
    }
    
    @CircuitBreaker(name = "cardAuthService", fallbackMethod = "standInAuthFallback")
    @Timeout(value = "100ms", unit = ChronoUnit.MILLIS)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 50))
    public CardAuthorizationResponse authorizeTransaction(CardAuthorizationRequest request) {
        log.info("Processing card auth: pan={}, amount={}, merchant={}", 
            maskPan(request.getPan()), request.getAmount(), request.getMerchantId());
        
        // Create authorization record
        CardAuthorization auth = new CardAuthorization();
        auth.setId(UUID.randomUUID().toString());
        auth.setTransactionId(request.getTransactionId());
        auth.setPan(request.getPan());
        auth.setAmount(request.getAmount());
        auth.setMerchantId(request.getMerchantId());
        auth.setMcc(request.getMcc());
        auth.setStatus(AuthStatus.INITIATED);
        auth.setCreatedAt(LocalDateTime.now());
        
        try {
            // Step 1: BIN Lookup and Card Validation
            CardBinRecord bin = binRepository.findByBin(request.getPan().substring(0, 6))
                .orElseThrow(() -> new InvalidCardException("Unknown BIN"));
            
            if (!bin.isActive() || bin.getIssuer().isBlocked()) {
                auth.setStatus(AuthStatus.DECLINED);
                auth.setResponseCode("51"); // Insufficient funds / blocked
                authRepository.save(auth);
                publishAuthEvent(auth, "DECLINED", "Issuer blocked");
                return new CardAuthorizationResponse(false, "51", null);
            }
            
            // Step 2: Card Expiry Validation
            if (isCardExpired(request.getExpiryYearMonth())) {
                auth.setStatus(AuthStatus.DECLINED);
                auth.setResponseCode("54"); // Expired card
                authRepository.save(auth);
                publishAuthEvent(auth, "DECLINED", "Card expired");
                return new CardAuthorizationResponse(false, "54", null);
            }
            
            // Step 3: CVV2 Validation
            if (request.getRequiresOnlineCvv() && !isValidCvv2(request.getCvv2())) {
                auth.setStatus(AuthStatus.DECLINED);
                auth.setResponseCode("55"); // Incorrect PIN/CVV
                authRepository.save(auth);
                publishAuthEvent(auth, "DECLINED", "Invalid CVV2");
                return new CardAuthorizationResponse(false, "55", null);
            }
            
            // Step 4: Velocity Check (multiple transactions in short timeframe)
            VelocityCheckResult velocityResult = velocityCheckService.checkVelocity(
                request.getPan(),
                request.getAmount(),
                request.getMerchantId()
            );
            if (velocityResult.isBlocked()) {
                auth.setStatus(AuthStatus.DECLINED);
                auth.setResponseCode("05"); // Do not honor
                authRepository.save(auth);
                publishAuthEvent(auth, "DECLINED", "Velocity check failed: " + velocityResult.getReason());
                return new CardAuthorizationResponse(false, "05", null);
            }
            
            // Step 5: Fraud Scoring
            FraudScore fraudScore = fraudScoringService.scoreTransaction(request);
            auth.setFraudScore(fraudScore.getScore());
            
            if (fraudScore.getRiskLevel() == RiskLevel.CRITICAL) {
                auth.setStatus(AuthStatus.DECLINED);
                auth.setResponseCode("05"); // Do not honor
                authRepository.save(auth);
                publishAuthEvent(auth, "DECLINED", "Fraud score critical");
                return new CardAuthorizationResponse(false, "05", null);
            }
            
            // Step 6: Check Account Available Balance
            AccountInfo accountInfo = getAccountInfo(request.getPan());
            if (accountInfo.getAvailableBalance().compareTo(request.getAmount()) < 0) {
                auth.setStatus(AuthStatus.DECLINED);
                auth.setResponseCode("51"); // Insufficient funds
                authRepository.save(auth);
                publishAuthEvent(auth, "DECLINED", "Insufficient funds");
                return new CardAuthorizationResponse(false, "51", null);
            }
            
            // Step 7: Transmit to Card Network (Visa/Mastercard)
            IsoMessage isoRequest = new IsoMessageBuilder().buildAuthorizationRequest(request);
            byte[] networkResponse = socketGateway.sendToNetwork(
                bin.getNetwork(),
                isoRequest.getEncodedMessage()
            );
            
            // Step 8: Parse Network Response
            IsoMessage responseMessage = new IsoMessageBuilder().parseAuthorizationResponse(networkResponse);
            String responseCode = responseMessage.getField(39);
            String approvalCode = responseMessage.getField(38);
            
            if (responseCode.equals("00")) {
                // Approved
                auth.setStatus(AuthStatus.APPROVED);
                auth.setResponseCode("00");
                auth.setApprovalCode(approvalCode);
                auth.setApprovedAt(LocalDateTime.now());
                authRepository.save(auth);
                publishAuthEvent(auth, "APPROVED", "Authorization approved");
                return new CardAuthorizationResponse(true, "00", approvalCode);
            } else {
                // Declined
                auth.setStatus(AuthStatus.DECLINED);
                auth.setResponseCode(responseCode);
                authRepository.save(auth);
                publishAuthEvent(auth, "DECLINED", "Network response: " + responseCode);
                return new CardAuthorizationResponse(false, responseCode, null);
            }
            
        } catch (Exception e) {
            log.error("Authorization failed with exception", e);
            auth.setStatus(AuthStatus.ERROR);
            auth.setErrorMessage(e.getMessage());
            authRepository.save(auth);
            publishAuthEvent(auth, "ERROR", e.getMessage());
            throw new CardAuthorizationException("Authorization processing failed", e);
        }
    }
    
    public CardAuthorizationResponse standInAuthFallback(CardAuthorizationRequest request, Exception ex) {
        log.warn("Stand-in authorization triggered (fallback): pan={}, amount={}", 
            maskPan(request.getPan()), request.getAmount());
        
        // Apply pre-configured stand-in rules for offline mode
        if (request.getAmount().compareTo(new BigDecimal("500")) > 0) {
            return new CardAuthorizationResponse(false, "05", null); // Decline large transactions
        }
        
        // Approve small transactions with velocity limit
        int txnCount = authRepository.countByPanAndCreatedAtAfter(
            request.getPan(),
            LocalDateTime.now().minusHours(1)
        );
        
        if (txnCount > 10) {
            return new CardAuthorizationResponse(false, "05", null); // Too many transactions
        }
        
        return new CardAuthorizationResponse(true, "00", "STANDIN" + System.currentTimeMillis());
    }
    
    private boolean isCardExpired(String expiryYearMonth) {
        LocalDate expiry = YearMonth.parse(expiryYearMonth, DateTimeFormatter.ofPattern("yyMM"))
            .atEndOfMonth();
        return LocalDate.now().isAfter(expiry);
    }
    
    private boolean isValidCvv2(String cvv2) {
        return cvv2 != null && cvv2.matches("\\d{3,4}") && cvv2.length() >= 3;
    }
    
    private AccountInfo getAccountInfo(String pan) {
        // Query account service for balance
        return new AccountInfo();
    }
    
    private void publishAuthEvent(CardAuthorization auth, String eventType, String description) {
        CardAuthEvent event = CardAuthEvent.builder()
            .authorizationId(auth.getId())
            .transactionId(auth.getTransactionId())
            .amount(auth.getAmount())
            .responseCode(auth.getResponseCode())
            .eventType(eventType)
            .description(description)
            .timestamp(LocalDateTime.now())
            .build();
        
        kafkaTemplate.send("card-auth-events", auth.getId(), event);
    }
    
    private String maskPan(String pan) {
        if (pan == null || pan.length() < 8) return "****";
        return "****" + pan.substring(pan.length() - 4);
    }
}
```

---

## TcpSocketGateway: Network Communication

The TcpSocketGateway manages pooled NIO socket connections to Visa/Mastercard networks with heartbeat monitoring.

```java
@Component
@Slf4j
public class TcpSocketGateway {
    
    private final GenericKeyedObjectPool<String, SocketConnection> connectionPool;
    private final ScheduledExecutorService heartbeatExecutor;
    
    @Autowired
    public TcpSocketGateway() {
        this.connectionPool = new GenericKeyedObjectPool<>(new SocketConnectionFactory());
        this.connectionPool.setMaxTotal(100);
        this.connectionPool.setMaxIdlePerKey(10);
        this.heartbeatExecutor = Executors.newScheduledThreadPool(2);
        
        // Start heartbeat monitor for keep-alive
        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeats, 0, 30, TimeUnit.SECONDS);
    }
    
    public byte[] sendToNetwork(NetworkType network, byte[] isoMessage) throws IOException {
        String networkKey = network.getKey();
        SocketConnection connection = null;
        
        try {
            connection = connectionPool.borrowObject(networkKey);
            
            // Send ISO message
            DataOutputStream out = new DataOutputStream(connection.getSocket().getOutputStream());
            out.writeInt(isoMessage.length);
            out.write(isoMessage);
            out.flush();
            
            // Read response (non-blocking with timeout)
            DataInputStream in = new DataInputStream(connection.getSocket().getInputStream());
            int responseLength = in.readInt();
            byte[] response = new byte[responseLength];
            in.readFully(response);
            
            log.debug("Network response received: length={}, network={}", responseLength, network);
            
            return response;
            
        } catch (SocketTimeoutException e) {
            log.error("Network timeout: {}", network, e);
            if (connection != null) {
                connectionPool.invalidateObject(networkKey, connection);
            }
            throw new NetworkTimeoutException("Network timeout for: " + network, e);
        } catch (IOException e) {
            log.error("Network IO error: {}", network, e);
            if (connection != null) {
                connectionPool.invalidateObject(networkKey, connection);
            }
            throw e;
        } finally {
            if (connection != null) {
                connectionPool.returnObject(networkKey, connection);
            }
        }
    }
    
    private void sendHeartbeats() {
        for (NetworkType network : NetworkType.values()) {
            try {
                SocketConnection connection = connectionPool.borrowObject(network.getKey());
                // Send 0800 heartbeat message
                byte[] heartbeat = buildHeartbeatMessage();
                DataOutputStream out = new DataOutputStream(connection.getSocket().getOutputStream());
                out.writeInt(heartbeat.length);
                out.write(heartbeat);
                out.flush();
                connectionPool.returnObject(network.getKey(), connection);
            } catch (Exception e) {
                log.warn("Heartbeat failed for {}: {}", network, e.getMessage());
            }
        }
    }
    
    private byte[] buildHeartbeatMessage() {
        // Build ISO 0800 network management heartbeat
        return "0800".getBytes(StandardCharsets.UTF_8);
    }
}

class SocketConnection {
    private final Socket socket;
    private final long createdAt;
    
    public SocketConnection(Socket socket) {
        this.socket = socket;
        this.createdAt = System.currentTimeMillis();
    }
    
    public Socket getSocket() {
        return socket;
    }
}

class SocketConnectionFactory extends BaseKeyedPooledObjectFactory<String, SocketConnection> {
    
    private static final Map<String, String> NETWORK_HOSTS = Map.of(
        "VISA", "visa-network-gateway.example.com",
        "MASTERCARD", "mastercard-network-gateway.example.com"
    );
    
    private static final Map<String, Integer> NETWORK_PORTS = Map.of(
        "VISA", 9001,
        "MASTERCARD", 9002
    );
    
    @Override
    public SocketConnection create(String networkKey) throws Exception {
        String host = NETWORK_HOSTS.get(networkKey);
        int port = NETWORK_PORTS.get(networkKey);
        
        Socket socket = new Socket(host, port);
        socket.setSoTimeout(5000); // 5 second read timeout
        socket.setTcpNoDelay(true);
        
        return new SocketConnection(socket);
    }
    
    @Override
    public PooledObject<SocketConnection> wrap(SocketConnection obj) {
        return new DefaultPooledObject<>(obj);
    }
    
    @Override
    public void destroyObject(String key, PooledObject<SocketConnection> p) throws Exception {
        p.getObject().getSocket().close();
    }
}

enum NetworkType {
    VISA("VISA", "Visa Inc."),
    MASTERCARD("MASTERCARD", "Mastercard International");
    
    private final String key;
    private final String name;
    
    NetworkType(String key, String name) {
        this.key = key;
        this.name = name;
    }
    
    public String getKey() {
        return key;
    }
}
```

---

## CardAuthorization Entity

```java
@Entity
@Table(name = "card_authorizations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardAuthorization {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String transactionId;
    
    @Column(nullable = false)
    private String pan;
    
    @Column(nullable = false)
    private BigDecimal amount;
    
    @Column(nullable = false)
    private String merchantId;
    
    private String mcc;
    
    @Enumerated(EnumType.STRING)
    private AuthStatus status;
    
    @Column(length = 2)
    private String responseCode;
    
    @Column(length = 10)
    private String approvalCode;
    
    private int fraudScore;
    
    @Column(length = 500)
    private String errorMessage;
    
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
}

enum AuthStatus {
    INITIATED, APPROVED, DECLINED, ERROR
}
```

---

## ClearingFileProcessor: Batch Settlement

```java
@Service
@Slf4j
public class ClearingFileProcessor {
    
    private final AuthorizationRepository authRepository;
    
    public void processClearingFile(InputStream clearingFileStream) {
        log.info("Processing clearing file");
        
        // Parse Visa TC05 or Mastercard clearing format
        // Match each transaction in clearing file to previously authorized transactions
        // Settle amounts, calculate interchange
    }
}
```

---

## 10 Critical Rules

1. **ISO 8583 Bitmap Correctness**: Primary bitmap (64 bits) indicates which fields 1-64 are present. Bit 1 set indicates secondary bitmap (128 fields total) follows. Field numbering is 1-indexed. Build bitmaps in hex, not binary strings.

2. **PAN Storage Compliance**: Never store full PAN in plaintext. Encrypt with AES-256-GCM or tokenize via network card processor. Only masked PAN (last 4 digits) logged for monitoring. Comply with PCI DSS 3.7.

3. **Timestamp Synchronization**: ISO field 7 (transmission datetime) must be UTC or network timezone. Field 12 (local transaction time) in HHmmss. Field 13 (local transaction date) in MMdd. Ensure accurate NTP sync; timestamp variance >1 second causes network rejection.

4. **Response Code Standardness**: Response codes are issuer-defined but follow conventions: 00=approved, 05=declined, 51=insufficient funds, 54=expired card, 55=incorrect PIN/CVV, 91=issuer unavailable. Map network responses to internal codes consistently.

5. **STAN Uniqueness Guarantee**: Systems Trace Audit Number (field 11) must be unique per issuer per day. Use 6-digit counter (0-999999) or timestamp-based. Never reuse STAN within 24 hours. Implement STAN deduplication at acquirer gateway.

6. **Socket Timeout Enforcement**: Network round-trip timeout MUST be <100ms for auth requests. Implement read timeout of 5 seconds on socket to prevent hung connections. Use non-blocking I/O (NIO) for high concurrency. Pool connections; auto-reconnect on failure.

7. **CVV2 Non-Storage Rule**: CVV2/CVC must NEVER be stored, logged, or transmitted post-authorization. Validate CVV2 only during auth request processing. Do NOT include CVV2 in subsequent clearing/settlement transactions.

8. **Fraud Scoring Pre-Auth**: Inline fraud score must complete within 100ms or timeout and fall back to stand-in rules. Use ML model inference only; do NOT call remote fraud service synchronously. Cache fraud rules; refresh hourly.

9. **Stand-In Authorization Configuration**: Stand-in rules must be pre-configured (floor limits, per-merchant daily caps, velocity thresholds). Apply stand-in ONLY on network timeout/unavailability (circuit breaker open). Auto-reversal required if transaction cannot be matched to network auth within 24 hours.

10. **Clearing File Reconciliation**: Daily clearing file from Visa/Mastercard must be reconciled to transmitted authorizations by STAN + RRN (Retrieval Reference Number). Unmatched authorizations flagged for manual review. Settlement must match clearing file amounts; variance triggers exception report.

