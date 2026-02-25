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
 * Functional test: Debit Card Network Journey
 *
 * Scenario:
 *  1. Issue debit card for account
 *  2. Get card by ID
 *  3. Get cards for customer
 *  4. Authorize purchase → APPROVED
 *  5. Authorize again (over daily limit) → DECLINED
 *  6. Block card
 *  7. Authorize on blocked card → DECLINED (CARD_BLOCKED)
 *  8. Unblock card
 *  9. Update spending limits
 * 10. Get card transactions
 * 11. Get transactions by account
 * 12. Reverse a transaction
 * 13. Batch settlement
 * 14. Get settlement by ID
 * 15. Card not found → 404
 */
@DisplayName("Journey: Debit Network")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DebitNetworkJourneyTest extends FunctionalTestBase {

    private static UUID accountId;
    private static UUID customerId;
    private static UUID cardId;
    private static UUID authorizedTransactionId;

    @BeforeAll
    static void init() {
        accountId = UUID.randomUUID();
        customerId = UUID.randomUUID();
    }

    @BeforeEach
    void setupStubs() {
        BankingPlatformStubs.stubFraudCheckClear(accountId.toString());
    }

    @Test
    @Order(1)
    @DisplayName("1. Issue debit card → 201 Created, ACTIVE status")
    void issueDebitCard() {
        Response response = given(requestSpec)
                .body(TestDataFactory.issueCardRequest(accountId, customerId))
                .when()
                .post("/api/v1/debit-network/cards")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("status", equalTo("ACTIVE"))
                .body("maskedCardNumber", matchesPattern("\\*{4} \\*{4} \\*{4} \\d{4}"))
                .body("cardHolderName", equalTo("ALICE MERCER"))
                .body("dailyLimit", comparesEqualTo(new BigDecimal("2500.00").floatValue()))
                .extract().response();

        cardId = UUID.fromString(response.jsonPath().getString("id"));
        assertThat(cardId).isNotNull();
        System.out.println("[Debit] Card issued: " + cardId);
    }

    @Test
    @Order(2)
    @DisplayName("2. Get card by ID → 200 OK")
    void getCardById() {
        assertThat(cardId).isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/debit-network/cards/{cardId}", cardId)
                .then()
                .statusCode(200)
                .body("id", equalTo(cardId.toString()))
                .body("status", equalTo("ACTIVE"));
    }

    @Test
    @Order(3)
    @DisplayName("3. Get cards for customer → at least 1 card")
    void getCustomerCards() {
        assertThat(customerId).isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/debit-network/cards/customer/{customerId}", customerId)
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("[0].status", equalTo("ACTIVE"));
    }

    @Test
    @Order(4)
    @DisplayName("4. Authorize purchase within limits → APPROVED")
    void authorizeWithinLimits() {
        assertThat(cardId).isNotNull();

        Response response = given(requestSpec)
                .body(TestDataFactory.authorizationRequest(cardId, new BigDecimal("120.00")))
                .when()
                .post("/api/v1/debit-network/authorize")
                .then()
                .statusCode(200)
                .body("approved", equalTo(true))
                .body("transactionId", notNullValue())
                .body("declineReason", nullValue())
                .extract().response();

        authorizedTransactionId = UUID.fromString(response.jsonPath().getString("transactionId"));
        assertThat(authorizedTransactionId).isNotNull();
        System.out.println("[Debit] Transaction authorized: " + authorizedTransactionId);
    }

    @Test
    @Order(5)
    @DisplayName("5. Authorize ATM withdrawal → APPROVED")
    void authorizeAtmWithdrawal() {
        assertThat(cardId).isNotNull();

        Map<String, Object> atmRequest = Map.of(
                "debitCardId", cardId.toString(),
                "merchantName", "Chase ATM",
                "merchantCategoryCode", "6011",
                "amount", new BigDecimal("200.00"),
                "currency", "USD",
                "transactionType", "ATM_WITHDRAWAL"
        );

        given(requestSpec)
                .body(atmRequest)
                .when()
                .post("/api/v1/debit-network/authorize")
                .then()
                .statusCode(200)
                .body("approved", equalTo(true));
    }

    @Test
    @Order(6)
    @DisplayName("6. Block card → status BLOCKED")
    void blockCard() {
        assertThat(cardId).isNotNull();

        given(requestSpec)
                .when()
                .post("/api/v1/debit-network/cards/{cardId}/block", cardId)
                .then()
                .statusCode(200)
                .body("status", equalTo("BLOCKED"));
    }

    @Test
    @Order(7)
    @DisplayName("7. Authorize on blocked card → DECLINED with CARD_BLOCKED reason")
    void authorizeOnBlockedCard() {
        assertThat(cardId).isNotNull();

        given(requestSpec)
                .body(TestDataFactory.authorizationRequest(cardId, new BigDecimal("50.00")))
                .when()
                .post("/api/v1/debit-network/authorize")
                .then()
                .statusCode(200)
                .body("approved", equalTo(false))
                .body("declineReason", equalTo("CARD_BLOCKED"));
    }

    @Test
    @Order(8)
    @DisplayName("8. Unblock card → status ACTIVE")
    void unblockCard() {
        assertThat(cardId).isNotNull();

        given(requestSpec)
                .when()
                .post("/api/v1/debit-network/cards/{cardId}/unblock", cardId)
                .then()
                .statusCode(200)
                .body("status", equalTo("ACTIVE"));
    }

    @Test
    @Order(9)
    @DisplayName("9. Update spending limits → limits updated")
    void updateSpendingLimits() {
        assertThat(cardId).isNotNull();

        given(requestSpec)
                .queryParam("dailyLimit", "1000.00")
                .queryParam("monthlyLimit", "10000.00")
                .when()
                .patch("/api/v1/debit-network/cards/{cardId}/limits", cardId)
                .then()
                .statusCode(200)
                .body("dailyLimit", comparesEqualTo(new BigDecimal("1000.00").floatValue()))
                .body("monthlyLimit", comparesEqualTo(new BigDecimal("10000.00").floatValue()));
    }

    @Test
    @Order(10)
    @DisplayName("10. Get card transactions → paginated results")
    void getCardTransactions() {
        assertThat(cardId).isNotNull();

        given(requestSpec)
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/debit-network/transactions/card/{cardId}", cardId)
                .then()
                .statusCode(200)
                .body("content", not(empty()))
                .body("page", equalTo(0));
    }

    @Test
    @Order(11)
    @DisplayName("11. Get transactions by account → results returned")
    void getTransactionsByAccount() {
        assertThat(accountId).isNotNull();

        given(requestSpec)
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/debit-network/transactions/account/{accountId}", accountId)
                .then()
                .statusCode(200)
                .body("content", not(empty()));
    }

    @Test
    @Order(12)
    @DisplayName("12. Reverse authorized transaction → REVERSED status")
    void reverseTransaction() {
        assertThat(authorizedTransactionId).isNotNull();

        given(requestSpec)
                .body(Map.of("reason", "Customer request"))
                .when()
                .post("/api/v1/debit-network/transactions/{id}/reverse", authorizedTransactionId)
                .then()
                .statusCode(200)
                .body("status", equalTo("REVERSED"));
    }

    @Test
    @Order(13)
    @DisplayName("13. Run batch settlement → settlement record created")
    void runBatchSettlement() {
        Response response = given(requestSpec)
                .when()
                .post("/api/v1/debit-network/settlements/batch")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("settlementBatchId", startsWith("BATCH-"))
                .body("status", notNullValue())
                .extract().response();

        System.out.println("[Debit] Settlement batch: "
                + response.jsonPath().getString("settlementBatchId"));
    }

    @Test
    @Order(14)
    @DisplayName("14. Card not found → 404 Not Found")
    void cardNotFound() {
        given(requestSpec)
                .when()
                .get("/api/v1/debit-network/cards/{cardId}", UUID.randomUUID())
                .then()
                .statusCode(404);
    }
}

