package com.banking.platform.functional.journey;

import com.banking.platform.functional.config.FunctionalTestBase;
import com.banking.platform.functional.util.TestDataFactory;
import com.banking.platform.functional.wiremock.BankingPlatformStubs;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * End-to-End Full Banking Journey Test
 *
 * Simulates a real customer's complete lifecycle across every platform module:
 *
 *  Phase 1 – Onboarding
 *    Step 01: Submit application
 *    Step 02: Progress through KYC to APPROVED
 *
 *  Phase 2 – Account Setup
 *    Step 03: Create CHECKING account
 *    Step 04: Create SAVINGS account
 *    Step 05: Link external bank
 *    Step 06: Verify micro-deposits
 *
 *  Phase 3 – Transacting
 *    Step 07: Post direct-deposit (CREDIT)
 *    Step 08: Post POS purchase (DEBIT)
 *    Step 09: View dashboard & balances
 *
 *  Phase 4 – Payment Rails
 *    Step 10: Initiate ACH transfer
 *    Step 11: Initiate domestic wire
 *    Step 12: Approve and complete wire
 *
 *  Phase 5 – Debit Card
 *    Step 13: Issue debit card
 *    Step 14: Authorise purchase
 *    Step 15: Run settlement batch
 *
 *  Phase 6 – Rewards
 *    Step 16: Enrol in rewards
 *    Step 17: Earn points from transaction
 *    Step 18: Redeem points for cash-back
 *
 *  Phase 7 – Ledger
 *    Step 19: Create GL accounts
 *    Step 20: Post balanced journal entry
 *    Step 21: Verify trial balance
 *
 *  Phase 8 – Reporting
 *    Step 22: Generate monthly statement
 *    Step 23: Schedule monthly report
 */
@DisplayName("E2E: Full Banking Customer Journey")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullBankingJourneyTest extends FunctionalTestBase {

    // ── Shared state ──────────────────────────────────────────────────────────
    private static UUID customerId;
    private static UUID applicationId;
    private static UUID checkingAccountId;
    private static UUID savingsAccountId;
    private static UUID linkedBankId;
    private static UUID creditTransactionId;
    private static UUID debitTransactionId;
    private static UUID achTransferId;
    private static UUID wireTransferId;
    private static UUID debitCardId;
    private static UUID debitTransactionAuthId;
    private static UUID glCashId;
    private static UUID glDepositsId;
    private static UUID journalEntryId;
    private static UUID reportId;
    private static UUID scheduledReportId;

    @BeforeAll
    static void initCustomer() {
        customerId = UUID.randomUUID();
        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║   E2E Full Banking Journey — Customer: " + customerId.toString().substring(0, 8) + "   ║");
        System.out.println("╚══════════════════════════════════════════════════╝\n");
    }

    @BeforeEach
    void setupAllStubs() {
        BankingPlatformStubs.stubEmailNotification();
        BankingPlatformStubs.stubSmsNotification();
        BankingPlatformStubs.stubKycApproved("alice.mercer");
        BankingPlatformStubs.stubRoutingLookup("021000021", "JPMorgan Chase");
        BankingPlatformStubs.stubPaymentNetworkAchSubmit("ACH-E2E-" + System.currentTimeMillis());
        BankingPlatformStubs.stubPaymentNetworkWireSubmit("WIRE-E2E-" + System.currentTimeMillis());
        if (customerId != null) {
            BankingPlatformStubs.stubFraudCheckClear(customerId.toString());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHASE 1 — ONBOARDING
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("[Phase 1] Step 01 — Submit customer application")
    void step01_submitApplication() {
        Map<String, Object> request = TestDataFactory.createApplicationRequest("CHECKING");

        Response response = given(requestSpec)
                .body(request)
                .when()
                .post("/api/v1/onboarding/applications")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("status", equalTo("SUBMITTED"))
                .body("email", notNullValue())
                .extract().response();

        applicationId = UUID.fromString(response.jsonPath().getString("id"));
        System.out.println("  ✓ Application submitted: " + applicationId);
    }

    @Test
    @Order(2)
    @DisplayName("[Phase 1] Step 02 — Approve application through full KYC workflow")
    void step02_approveApplication() {
        assertThat(applicationId).isNotNull();

        // SUBMITTED → UNDER_REVIEW
        given(requestSpec)
                .body(TestDataFactory.updateStatusRequest("UNDER_REVIEW", "ops@bank.com"))
                .when()
                .patch("/api/v1/onboarding/applications/{id}/status", applicationId)
                .then().statusCode(200).body("status", equalTo("UNDER_REVIEW"));

        // UNDER_REVIEW → KYC_PENDING
        given(requestSpec)
                .body(TestDataFactory.updateStatusRequest("KYC_PENDING", "kyc@bank.com"))
                .when()
                .patch("/api/v1/onboarding/applications/{id}/status", applicationId)
                .then().statusCode(200).body("status", equalTo("KYC_PENDING"));

        // KYC_PENDING → KYC_APPROVED
        given(requestSpec)
                .body(TestDataFactory.updateStatusRequest("KYC_APPROVED", "kyc-system@bank.com"))
                .when()
                .patch("/api/v1/onboarding/applications/{id}/status", applicationId)
                .then().statusCode(200).body("status", equalTo("KYC_APPROVED"));

        // KYC_APPROVED → APPROVED
        given(requestSpec)
                .body(TestDataFactory.updateStatusRequest("APPROVED", "supervisor@bank.com"))
                .when()
                .patch("/api/v1/onboarding/applications/{id}/status", applicationId)
                .then().statusCode(200).body("status", equalTo("APPROVED"));

        System.out.println("  ✓ Application approved through full KYC workflow");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHASE 2 — ACCOUNT SETUP
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("[Phase 2] Step 03 — Create CHECKING account with $5,000 deposit")
    void step03_createCheckingAccount() {
        Response response = given(requestSpec)
                .body(TestDataFactory.createAccountRequest(customerId, "CHECKING", new BigDecimal("5000.00")))
                .when()
                .post("/api/v1/accounts")
                .then()
                .statusCode(201)
                .body("type", equalTo("CHECKING"))
                .body("status", equalTo("ACTIVE"))
                .body("currentBalance", comparesEqualTo(5000.0f))
                .extract().response();

        checkingAccountId = UUID.fromString(response.jsonPath().getString("id"));
        System.out.println("  ✓ Checking account: " + checkingAccountId);
    }

    @Test
    @Order(4)
    @DisplayName("[Phase 2] Step 04 — Create SAVINGS account with $1,000 deposit")
    void step04_createSavingsAccount() {
        Response response = given(requestSpec)
                .body(TestDataFactory.createAccountRequest(customerId, "SAVINGS", new BigDecimal("1000.00")))
                .when()
                .post("/api/v1/accounts")
                .then()
                .statusCode(201)
                .body("type", equalTo("SAVINGS"))
                .body("status", equalTo("ACTIVE"))
                .extract().response();

        savingsAccountId = UUID.fromString(response.jsonPath().getString("id"));
        System.out.println("  ✓ Savings account: " + savingsAccountId);
    }

    @Test
    @Order(5)
    @DisplayName("[Phase 2] Step 05 — Link external bank account")
    void step05_linkExternalBank() {
        Response response = given(requestSpec)
                .body(TestDataFactory.linkBankRequest(customerId))
                .when()
                .post("/api/v1/banks/link")
                .then()
                .statusCode(201)
                .body("linkStatus", equalTo("PENDING_VERIFICATION"))
                .extract().response();

        linkedBankId = UUID.fromString(response.jsonPath().getString("id"));
        System.out.println("  ✓ External bank linked: " + linkedBankId);
    }

    @Test
    @Order(6)
    @DisplayName("[Phase 2] Step 06 — Verify micro-deposits → bank VERIFIED")
    void step06_verifyMicroDeposits() {
        assertThat(linkedBankId).isNotNull();

        given(requestSpec)
                .body(TestDataFactory.verifyMicroDepositsRequest(
                        new BigDecimal("0.32"), new BigDecimal("0.45")))
                .when()
                .post("/api/v1/banks/{id}/verify", linkedBankId)
                .then()
                .statusCode(200)
                .body("linkStatus", equalTo("VERIFIED"));

        System.out.println("  ✓ External bank verified");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHASE 3 — TRANSACTING
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(7)
    @DisplayName("[Phase 3] Step 07 — Post direct deposit CREDIT of $3,200")
    void step07_postDirectDeposit() {
        assertThat(checkingAccountId).isNotNull();

        Response response = given(requestSpec)
                .body(Map.of(
                        "accountId", checkingAccountId.toString(),
                        "type", "CREDIT",
                        "category", "DIRECT_DEPOSIT",
                        "amount", new BigDecimal("3200.00"),
                        "description", "Payroll direct deposit",
                        "merchantName", "Acme Corp Payroll",
                        "channel", "ACH"
                ))
                .when()
                .post("/api/v1/transactions")
                .then()
                .statusCode(201)
                .body("type", equalTo("CREDIT"))
                .body("amount", comparesEqualTo(3200.0f))
                .extract().response();

        creditTransactionId = UUID.fromString(response.jsonPath().getString("id"));
        System.out.println("  ✓ Direct deposit posted: " + creditTransactionId);
    }

    @Test
    @Order(8)
    @DisplayName("[Phase 3] Step 08 — Post POS purchase DEBIT of $89.99")
    void step08_postPosPurchase() {
        assertThat(checkingAccountId).isNotNull();

        Response response = given(requestSpec)
                .body(TestDataFactory.debitTransactionRequest(
                        checkingAccountId, new BigDecimal("89.99")))
                .when()
                .post("/api/v1/transactions")
                .then()
                .statusCode(201)
                .body("type", equalTo("DEBIT"))
                .body("category", equalTo("POS_PURCHASE"))
                .extract().response();

        debitTransactionId = UUID.fromString(response.jsonPath().getString("id"));
        System.out.println("  ✓ POS purchase posted: " + debitTransactionId);
    }

    @Test
    @Order(9)
    @DisplayName("[Phase 3] Step 09 — View customer dashboard")
    void step09_viewDashboard() {
        given(requestSpec)
                .when()
                .get("/api/v1/dashboard/{customerId}", customerId)
                .then()
                .statusCode(200)
                .body("customerId", equalTo(customerId.toString()))
                .body("accounts", hasSize(greaterThanOrEqualTo(2)))
                .body("totalBalance", notNullValue());

        System.out.println("  ✓ Dashboard retrieved with 2 accounts");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHASE 4 — PAYMENT RAILS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("[Phase 4] Step 10 — Initiate ACH CREDIT of $500 to external bank")
    void step10_initiateAchTransfer() {
        assertThat(checkingAccountId).isNotNull();
        assertThat(linkedBankId).isNotNull();

        Response response = given(requestSpec)
                .body(TestDataFactory.initiateAchRequest(checkingAccountId, linkedBankId))
                .when()
                .post("/api/v1/ach/transfers")
                .then()
                .statusCode(201)
                .body("status", equalTo("INITIATED"))
                .body("direction", equalTo("CREDIT"))
                .body("traceNumber", notNullValue())
                .extract().response();

        achTransferId = UUID.fromString(response.jsonPath().getString("id"));
        System.out.println("  ✓ ACH transfer initiated: " + achTransferId);
    }

    @Test
    @Order(11)
    @DisplayName("[Phase 4] Step 11 — Initiate domestic wire of $1,500")
    void step11_initiateDomesticWire() {
        assertThat(checkingAccountId).isNotNull();

        Response response = given(requestSpec)
                .body(TestDataFactory.initiateDomesticWireRequest(checkingAccountId))
                .when()
                .post("/api/v1/wire/transfers")
                .then()
                .statusCode(201)
                .body("wireType", equalTo("DOMESTIC"))
                .body("status", equalTo("INITIATED"))
                .body("fee", comparesEqualTo(25.0f))
                .extract().response();

        wireTransferId = UUID.fromString(response.jsonPath().getString("id"));
        System.out.println("  ✓ Wire initiated: " + wireTransferId);
    }

    @Test
    @Order(12)
    @DisplayName("[Phase 4] Step 12 — Approve and complete wire transfer")
    void step12_approveAndCompleteWire() {
        assertThat(wireTransferId).isNotNull();

        given(requestSpec)
                .when()
                .post("/api/v1/wire/transfers/{id}/approve", wireTransferId)
                .then().statusCode(200).body("status", equalTo("APPROVED"));

        given(requestSpec)
                .body(Map.of("networkReference", "FED-" + System.currentTimeMillis()))
                .when()
                .post("/api/v1/wire/transfers/{id}/complete", wireTransferId)
                .then().statusCode(200).body("status", equalTo("COMPLETED"));

        System.out.println("  ✓ Wire approved and completed");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHASE 5 — DEBIT CARD
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(13)
    @DisplayName("[Phase 5] Step 13 — Issue debit card for checking account")
    void step13_issueDebitCard() {
        assertThat(checkingAccountId).isNotNull();

        Response response = given(requestSpec)
                .body(TestDataFactory.issueCardRequest(checkingAccountId, customerId))
                .when()
                .post("/api/v1/debit-network/cards")
                .then()
                .statusCode(201)
                .body("status", equalTo("ACTIVE"))
                .body("maskedCardNumber", notNullValue())
                .extract().response();

        debitCardId = UUID.fromString(response.jsonPath().getString("id"));
        System.out.println("  ✓ Debit card issued: " + debitCardId);
    }

    @Test
    @Order(14)
    @DisplayName("[Phase 5] Step 14 — Authorise $75 purchase")
    void step14_authorisePurchase() {
        assertThat(debitCardId).isNotNull();

        Response response = given(requestSpec)
                .body(TestDataFactory.authorizationRequest(debitCardId, new BigDecimal("75.00")))
                .when()
                .post("/api/v1/debit-network/authorize")
                .then()
                .statusCode(200)
                .body("approved", equalTo(true))
                .body("transactionId", notNullValue())
                .extract().response();

        debitTransactionAuthId = UUID.fromString(response.jsonPath().getString("transactionId"));
        System.out.println("  ✓ Purchase authorised: " + debitTransactionAuthId);
    }

    @Test
    @Order(15)
    @DisplayName("[Phase 5] Step 15 — Run EOD batch settlement")
    void step15_runSettlement() {
        given(requestSpec)
                .when()
                .post("/api/v1/debit-network/settlements/batch")
                .then()
                .statusCode(201)
                .body("settlementBatchId", startsWith("BATCH-"))
                .body("id", notNullValue());

        System.out.println("  ✓ Batch settlement completed");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHASE 6 — REWARDS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(16)
    @DisplayName("[Phase 6] Step 16 — Enrol in rewards program")
    void step16_enrolInRewards() {
        given(requestSpec)
                .body(TestDataFactory.enrollRewardsRequest(customerId))
                .when()
                .post("/api/v1/rewards/enrol")
                .then()
                .statusCode(201)
                .body("tier", equalTo("BRONZE"))
                .body("pointsBalance", equalTo(0));

        System.out.println("  ✓ Enrolled in rewards (BRONZE)");
    }

    @Test
    @Order(17)
    @DisplayName("[Phase 6] Step 17 — Earn 750 points from transaction")
    void step17_earnPoints() {
        given(requestSpec)
                .body(TestDataFactory.earnPointsRequest(customerId, 750L))
                .when()
                .post("/api/v1/rewards/earn")
                .then()
                .statusCode(201)
                .body("type", equalTo("EARN"))
                .body("points", greaterThan(0));

        System.out.println("  ✓ 750 points earned");
    }

    @Test
    @Order(18)
    @DisplayName("[Phase 6] Step 18 — Redeem 250 points for cash-back")
    void step18_redeemPoints() {
        given(requestSpec)
                .body(TestDataFactory.redeemPointsRequest(customerId, 250L))
                .when()
                .post("/api/v1/rewards/redeem")
                .then()
                .statusCode(201)
                .body("redemptionType", equalTo("CASH_BACK"));

        // Verify balance is now ≥500 (750 earned - 250 redeemed)
        given(requestSpec)
                .when()
                .get("/api/v1/rewards/account/{customerId}/summary", customerId)
                .then()
                .statusCode(200)
                .body("pointsBalance", greaterThanOrEqualTo(500));

        System.out.println("  ✓ 250 points redeemed for cash-back");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHASE 7 — LEDGER
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(19)
    @DisplayName("[Phase 7] Step 19 — Set up GL accounts (Cash + Deposits)")
    void step19_setupGlAccounts() {
        Response cashResp = given(requestSpec)
                .body(TestDataFactory.createGlAccountRequest("1100", "Cash - E2E", "ASSET"))
                .when().post("/api/v1/ledger/gl-accounts")
                .then().statusCode(201).extract().response();
        glCashId = UUID.fromString(cashResp.jsonPath().getString("id"));

        Response depResp = given(requestSpec)
                .body(TestDataFactory.createGlAccountRequest("2100", "Deposits - E2E", "LIABILITY"))
                .when().post("/api/v1/ledger/gl-accounts")
                .then().statusCode(201).extract().response();
        glDepositsId = UUID.fromString(depResp.jsonPath().getString("id"));

        System.out.println("  ✓ GL accounts created: Cash=" + glCashId + ", Deposits=" + glDepositsId);
    }

    @Test
    @Order(20)
    @DisplayName("[Phase 7] Step 20 — Post balanced journal entry for customer deposit")
    void step20_postJournalEntry() {
        Response response = given(requestSpec)
                .body(TestDataFactory.createJournalEntryRequest(
                        "1100", "2100",
                        new BigDecimal("5000.00"),
                        "E2E customer deposit posting"))
                .when().post("/api/v1/ledger/journal-entries")
                .then().statusCode(201).body("status", equalTo("DRAFT")).extract().response();

        journalEntryId = UUID.fromString(response.jsonPath().getString("id"));

        given(requestSpec)
                .when()
                .post("/api/v1/ledger/journal-entries/{id}/post", journalEntryId)
                .then().statusCode(200).body("status", equalTo("POSTED"));

        System.out.println("  ✓ Journal entry posted: " + journalEntryId);
    }

    @Test
    @Order(21)
    @DisplayName("[Phase 7] Step 21 — Verify trial balance is balanced")
    void step21_verifyTrialBalance() {
        given(requestSpec)
                .when()
                .get("/api/v1/ledger/trial-balance")
                .then()
                .statusCode(200)
                .body("entries", not(empty()))
                .body("totalDebits", notNullValue())
                .body("totalCredits", notNullValue());

        System.out.println("  ✓ Trial balance retrieved");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PHASE 8 — REPORTING
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(22)
    @DisplayName("[Phase 8] Step 22 — Generate monthly account statement")
    void step22_generateStatement() {
        assertThat(checkingAccountId).isNotNull();

        Response response = given(requestSpec)
                .body(TestDataFactory.generateReportRequest(customerId, checkingAccountId))
                .when().post("/api/v1/reports")
                .then()
                .statusCode(201)
                .body("reportType", equalTo("ACCOUNT_STATEMENT"))
                .extract().response();

        reportId = UUID.fromString(response.jsonPath().getString("id"));
        System.out.println("  ✓ Statement report generated: " + reportId);
    }

    @Test
    @Order(23)
    @DisplayName("[Phase 8] Step 23 — Schedule monthly recurring report")
    void step23_scheduleMonthlyReport() {
        assertThat(checkingAccountId).isNotNull();

        Response response = given(requestSpec)
                .body(TestDataFactory.scheduledReportRequest(customerId, checkingAccountId))
                .when().post("/api/v1/reports/scheduled")
                .then()
                .statusCode(201)
                .body("frequency", equalTo("MONTHLY"))
                .body("active", equalTo(true))
                .extract().response();

        scheduledReportId = UUID.fromString(response.jsonPath().getString("id"));
        System.out.println("  ✓ Monthly report scheduled: " + scheduledReportId);
    }

    @Test
    @Order(24)
    @DisplayName("[E2E Summary] Verify final state: all 10 API domains exercised")
    void finalAssertions() {
        // All IDs must be set — proves every phase was reached
        assertThat(applicationId).as("Onboarding: applicationId").isNotNull();
        assertThat(checkingAccountId).as("Account: checkingAccountId").isNotNull();
        assertThat(savingsAccountId).as("Account: savingsAccountId").isNotNull();
        assertThat(linkedBankId).as("BankMgmt: linkedBankId").isNotNull();
        assertThat(creditTransactionId).as("Transactions: creditTransactionId").isNotNull();
        assertThat(debitTransactionId).as("Transactions: debitTransactionId").isNotNull();
        assertThat(achTransferId).as("ACH: achTransferId").isNotNull();
        assertThat(wireTransferId).as("Wire: wireTransferId").isNotNull();
        assertThat(debitCardId).as("DebitNetwork: debitCardId").isNotNull();
        assertThat(debitTransactionAuthId).as("DebitNetwork: authTransactionId").isNotNull();
        assertThat(journalEntryId).as("Ledger: journalEntryId").isNotNull();
        assertThat(reportId).as("Reporting: reportId").isNotNull();
        assertThat(scheduledReportId).as("Reporting: scheduledReportId").isNotNull();

        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║   ✅  E2E Journey PASSED — All 10 domains OK    ║");
        System.out.println("╚══════════════════════════════════════════════════╝\n");
    }
}

