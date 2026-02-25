package com.banking.platform.functional.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for generating deterministic, realistic test payloads for all API endpoints.
 *
 * Uses a counter-suffix to ensure email/account uniqueness across test runs.
 */
public final class TestDataFactory {

    private static final AtomicLong COUNTER = new AtomicLong(System.currentTimeMillis());

    private TestDataFactory() {}

    private static long next() {
        return COUNTER.incrementAndGet();
    }

    // ── Onboarding ────────────────────────────────────────────────────────────

    public static Map<String, Object> createApplicationRequest() {
        return createApplicationRequest("CHECKING");
    }

    public static Map<String, Object> createApplicationRequest(String accountType) {
        long id = next();
        Map<String, Object> req = new HashMap<>();
        req.put("firstName", "Alice");
        req.put("lastName", "Mercer");
        req.put("email", "alice.mercer." + id + "@testbank.com");
        req.put("phone", "+12025551234");
        req.put("dateOfBirth", "1988-04-15");
        req.put("addressLine1", "123 Main Street");
        req.put("addressLine2", "Apt 4B");
        req.put("city", "New York");
        req.put("state", "NY");
        req.put("zipCode", "10001");
        req.put("country", "USA");
        req.put("requestedAccountType", accountType);
        return req;
    }

    public static Map<String, Object> updateStatusRequest(String status, String reviewedBy) {
        return Map.of("status", status, "reviewedBy", reviewedBy);
    }

    // ── Account ───────────────────────────────────────────────────────────────

    public static Map<String, Object> createAccountRequest(UUID customerId) {
        return createAccountRequest(customerId, "CHECKING", new BigDecimal("5000.00"));
    }

    public static Map<String, Object> createAccountRequest(UUID customerId, String type,
                                                            BigDecimal initialDeposit) {
        return Map.of(
                "customerId", customerId.toString(),
                "accountType", type,
                "initialDeposit", initialDeposit,
                "currency", "USD"
        );
    }

    // ── Transaction ───────────────────────────────────────────────────────────

    public static Map<String, Object> createTransactionRequest(UUID accountId,
                                                                String type,
                                                                BigDecimal amount) {
        return Map.of(
                "accountId", accountId.toString(),
                "type", type,
                "category", "DIRECT_DEPOSIT",
                "amount", amount,
                "description", "Test transaction " + next(),
                "merchantName", "ACME Corp",
                "channel", "ONLINE"
        );
    }

    public static Map<String, Object> debitTransactionRequest(UUID accountId, BigDecimal amount) {
        return Map.of(
                "accountId", accountId.toString(),
                "type", "DEBIT",
                "category", "POS_PURCHASE",
                "amount", amount,
                "description", "POS purchase at coffee shop",
                "merchantName", "Blue Bottle Coffee",
                "merchantCategory", "FOOD",
                "channel", "ONLINE"
        );
    }

    public static Map<String, Object> transactionSearchRequest(UUID accountId,
                                                                String type, String status) {
        return Map.of(
                "accountId", accountId.toString(),
                "type", type,
                "status", status
        );
    }

    // ── ACH ───────────────────────────────────────────────────────────────────

    public static Map<String, Object> initiateAchRequest(UUID accountId, UUID linkedBankId) {
        return Map.of(
                "accountId", accountId.toString(),
                "linkedBankId", linkedBankId.toString(),
                "direction", "CREDIT",
                "achType", "STANDARD",
                "amount", new BigDecimal("250.00"),
                "memo", "ACH transfer test " + next(),
                "secCode", "PPD",
                "effectiveDate", LocalDate.now().plusDays(1).toString()
        );
    }

    public static Map<String, Object> achReturnRequest(String returnCode) {
        return Map.of("returnCode", returnCode, "returnReason", "Account closed");
    }

    // ── Wire ──────────────────────────────────────────────────────────────────

    public static Map<String, Object> initiateDomesticWireRequest(UUID accountId) {
        return Map.of(
                "accountId", accountId.toString(),
                "wireType", "DOMESTIC",
                "amount", new BigDecimal("1500.00"),
                "currency", "USD",
                "beneficiaryName", "Bob Johnson",
                "beneficiaryAccountNumber", "987654321012",
                "beneficiaryRoutingNumber", "021000021",
                "beneficiaryBankName", "Chase Bank",
                "purposeOfWire", "Invoice payment",
                "memo", "Wire test " + next()
        );
    }

    public static Map<String, Object> initiateInternationalWireRequest(UUID accountId) {
        Map<String, Object> req = new HashMap<>();
        req.put("accountId", accountId.toString());
        req.put("wireType", "INTERNATIONAL");
        req.put("amount", new BigDecimal("5000.00"));
        req.put("currency", "EUR");
        req.put("beneficiaryName", "Hans Mueller");
        req.put("beneficiaryAccountNumber", "DE89370400440532013000");
        req.put("beneficiaryBankName", "Deutsche Bank");
        req.put("beneficiarySwiftCode", "DEUTDEDB");
        req.put("beneficiaryIban", "DE89370400440532013000");
        req.put("purposeOfWire", "Contract payment");
        req.put("memo", "International wire test " + next());
        return req;
    }

    // ── Bank Management ───────────────────────────────────────────────────────

    public static Map<String, Object> linkBankRequest(UUID customerId) {
        return Map.of(
                "customerId", customerId.toString(),
                "routingNumber", "021000021",
                "accountNumber", "1234567890" + next(),
                "accountType", "CHECKING",
                "accountHolderName", "Alice Mercer",
                "nickname", "My Chase Checking",
                "verificationMethod", "MICRO_DEPOSIT"
        );
    }

    public static Map<String, Object> verifyMicroDepositsRequest(BigDecimal d1, BigDecimal d2) {
        return Map.of("deposit1", d1, "deposit2", d2);
    }

    // ── Rewards ───────────────────────────────────────────────────────────────

    public static Map<String, Object> enrollRewardsRequest(UUID customerId) {
        return Map.of("customerId", customerId.toString());
    }

    public static Map<String, Object> earnPointsRequest(UUID customerId, long points) {
        return Map.of(
                "customerId", customerId.toString(),
                "basePoints", points,
                "description", "Points for transaction " + next()
        );
    }

    public static Map<String, Object> redeemPointsRequest(UUID customerId, long points) {
        return Map.of(
                "customerId", customerId.toString(),
                "pointsToRedeem", points,
                "redemptionType", "CASH_BACK",
                "description", "Cash back redemption"
        );
    }

    // ── Debit Network ─────────────────────────────────────────────────────────

    public static Map<String, Object> issueCardRequest(UUID accountId, UUID customerId) {
        return Map.of(
                "accountId", accountId.toString(),
                "customerId", customerId.toString(),
                "cardHolderName", "ALICE MERCER",
                "dailyLimit", new BigDecimal("2500.00"),
                "monthlyLimit", new BigDecimal("25000.00")
        );
    }

    public static Map<String, Object> authorizationRequest(UUID cardId, BigDecimal amount) {
        return Map.of(
                "debitCardId", cardId.toString(),
                "merchantName", "Amazon.com",
                "merchantCategoryCode", "5999",
                "amount", amount,
                "currency", "USD",
                "transactionType", "PURCHASE"
        );
    }

    // ── Ledger ────────────────────────────────────────────────────────────────

    public static Map<String, Object> createGlAccountRequest(String code, String name, String type) {
        return Map.of(
                "accountCode", code,
                "accountName", name,
                "accountType", type,
                "normalBalance", type.equals("ASSET") || type.equals("EXPENSE") ? "DEBIT" : "CREDIT",
                "description", name + " GL account"
        );
    }

    public static Map<String, Object> createJournalEntryRequest(String debitCode,
                                                                  String creditCode,
                                                                  BigDecimal amount,
                                                                  String description) {
        return Map.of(
                "entryDate", LocalDate.now().toString(),
                "description", description,
                "referenceType", "TEST",
                "lines", new Object[]{
                        Map.of("accountCode", debitCode, "side", "DEBIT",
                               "amount", amount, "description", "Debit leg"),
                        Map.of("accountCode", creditCode, "side", "CREDIT",
                               "amount", amount, "description", "Credit leg")
                }
        );
    }

    // ── Reporting ─────────────────────────────────────────────────────────────

    public static Map<String, Object> generateReportRequest(UUID customerId, UUID accountId) {
        return Map.of(
                "customerId", customerId.toString(),
                "accountId", accountId.toString(),
                "reportType", "ACCOUNT_STATEMENT",
                "fromDate", LocalDate.now().minusDays(30).toString(),
                "toDate", LocalDate.now().toString()
        );
    }

    public static Map<String, Object> scheduledReportRequest(UUID customerId, UUID accountId) {
        return Map.of(
                "customerId", customerId.toString(),
                "accountId", accountId.toString(),
                "reportType", "TRANSACTION_HISTORY",
                "frequency", "MONTHLY",
                "description", "Monthly statement"
        );
    }
}

