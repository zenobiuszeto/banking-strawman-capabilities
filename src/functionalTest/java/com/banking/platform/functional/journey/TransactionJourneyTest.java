package com.banking.platform.functional.journey;

import com.banking.platform.functional.config.FunctionalTestBase;
import com.banking.platform.functional.util.TestDataFactory;
import com.banking.platform.functional.wiremock.BankingPlatformStubs;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Functional test: Transaction Ledger Journey
 *
 * Scenario:
 *  1. Post a CREDIT transaction (direct deposit)
 *  2. Post a DEBIT transaction (POS purchase)
 *  3. Get transaction by ID
 *  4. Get transaction by reference number
 *  5. List transactions for account (paginated)
 *  6. Search transactions by type filter
 *  7. Get transaction summary for date range
 *  8. Reverse a transaction
 *  9. Zero amount → 400 validation error
 * 10. Non-existent transaction → 404
 */
@DisplayName("Journey: Transactions")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransactionJourneyTest extends FunctionalTestBase {

    private static UUID accountId;
    private static UUID creditTransactionId;
    private static UUID debitTransactionId;
    private static String referenceNumber;

    @BeforeAll
    static void init() {
        accountId = UUID.randomUUID();
    }

    @BeforeEach
    void setupStubs() {
        BankingPlatformStubs.stubEmailNotification();
        BankingPlatformStubs.stubFraudCheckClear(accountId.toString());
    }

    @Test
    @Order(1)
    @DisplayName("1. Post CREDIT transaction → 201 Created")
    void postCreditTransaction() {
        Response response = given(requestSpec)
                .body(TestDataFactory.createTransactionRequest(
                        accountId, "CREDIT", new BigDecimal("2500.00")))
                .when()
                .post("/api/v1/transactions")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("type", equalTo("CREDIT"))
                .body("amount", comparesEqualTo(new BigDecimal("2500.00").floatValue()))
                .body("status", equalTo("PENDING"))
                .body("referenceNumber", notNullValue())
                .extract().response();

        creditTransactionId = UUID.fromString(response.jsonPath().getString("id"));
        referenceNumber = response.jsonPath().getString("referenceNumber");
        assertThat(referenceNumber).startsWith("TXN-");
        System.out.println("[Transaction] Credit posted: " + creditTransactionId
                + " ref=" + referenceNumber);
    }

    @Test
    @Order(2)
    @DisplayName("2. Post DEBIT transaction → 201 Created")
    void postDebitTransaction() {
        Response response = given(requestSpec)
                .body(TestDataFactory.debitTransactionRequest(accountId, new BigDecimal("45.99")))
                .when()
                .post("/api/v1/transactions")
                .then()
                .statusCode(201)
                .body("type", equalTo("DEBIT"))
                .body("category", equalTo("POS_PURCHASE"))
                .body("merchantName", equalTo("Blue Bottle Coffee"))
                .extract().response();

        debitTransactionId = UUID.fromString(response.jsonPath().getString("id"));
        assertThat(debitTransactionId).isNotNull();
    }

    @Test
    @Order(3)
    @DisplayName("3. Get transaction by ID → 200 OK")
    void getTransactionById() {
        assertThat(creditTransactionId).isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/transactions/{id}", creditTransactionId)
                .then()
                .statusCode(200)
                .body("id", equalTo(creditTransactionId.toString()))
                .body("amount", comparesEqualTo(new BigDecimal("2500.00").floatValue()));
    }

    @Test
    @Order(4)
    @DisplayName("4. Get transaction by reference number → 200 OK")
    void getTransactionByReference() {
        assertThat(referenceNumber).isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/transactions/reference/{ref}", referenceNumber)
                .then()
                .statusCode(200)
                .body("referenceNumber", equalTo(referenceNumber));
    }

    @Test
    @Order(5)
    @DisplayName("5. List transactions for account → paginated results")
    void listTransactionsByAccount() {
        assertThat(accountId).isNotNull();

        given(requestSpec)
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/transactions/account/{accountId}", accountId)
                .then()
                .statusCode(200)
                .body("content", hasSize(greaterThanOrEqualTo(2)))
                .body("totalElements", greaterThanOrEqualTo(2))
                .body("page", equalTo(0));
    }

    @Test
    @Order(6)
    @DisplayName("6. Search transactions with type filter → filtered results")
    void searchTransactions() {
        assertThat(accountId).isNotNull();

        Map<String, Object> searchRequest = Map.of(
                "accountId", accountId.toString(),
                "type", "CREDIT"
        );

        given(requestSpec)
                .body(searchRequest)
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .post("/api/v1/transactions/search")
                .then()
                .statusCode(200)
                .body("content", not(empty()))
                .body("content.type", everyItem(equalTo("CREDIT")));
    }

    @Test
    @Order(7)
    @DisplayName("7. Get transaction summary for date range → totals present")
    void getTransactionSummary() {
        assertThat(accountId).isNotNull();

        given(requestSpec)
                .queryParam("fromDate", LocalDate.now().minusDays(7).toString())
                .queryParam("toDate", LocalDate.now().toString())
                .when()
                .get("/api/v1/transactions/account/{accountId}/summary", accountId)
                .then()
                .statusCode(200)
                .body("totalCredits", notNullValue())
                .body("totalDebits", notNullValue())
                .body("transactionCount", greaterThanOrEqualTo(0));
    }

    @Test
    @Order(8)
    @DisplayName("8. Reverse DEBIT transaction → 200 OK with status REVERSED")
    void reverseTransaction() {
        assertThat(debitTransactionId).isNotNull();

        given(requestSpec)
                .body(Map.of("reason", "Customer dispute"))
                .when()
                .post("/api/v1/transactions/{id}/reverse", debitTransactionId)
                .then()
                .statusCode(200)
                .body("status", equalTo("REVERSED"));
    }

    @Test
    @Order(9)
    @DisplayName("9. Zero amount transaction → 400 Bad Request")
    void zeroAmountRejected() {
        given(requestSpec)
                .body(TestDataFactory.createTransactionRequest(accountId, "DEBIT", BigDecimal.ZERO))
                .when()
                .post("/api/v1/transactions")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(10)
    @DisplayName("10. Transaction not found → 404 Not Found")
    void transactionNotFound() {
        given(requestSpec)
                .when()
                .get("/api/v1/transactions/{id}", UUID.randomUUID())
                .then()
                .statusCode(404);
    }
}

