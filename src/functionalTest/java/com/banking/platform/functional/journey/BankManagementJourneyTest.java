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
 * Functional test: Bank Management Journey
 *
 * Scenario:
 *  1.  Search bank directory by name
 *  2.  Link external bank (MICRO_DEPOSIT verification)
 *  3.  Get linked banks for customer
 *  4.  Get linked bank by ID
 *  5.  Verify micro-deposits (correct amounts) → VERIFIED
 *  6.  Set bank as primary
 *  7.  Link second bank (INSTANT_VERIFICATION)
 *  8.  Get updated bank list → 2 banks
 *  9.  Verify micro-deposits with wrong amounts → fails
 * 10.  Remove linked bank → 204 No Content
 * 11.  Linked bank not found → 404
 * 12.  Missing required fields → 400
 */
@DisplayName("Journey: Bank Management")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BankManagementJourneyTest extends FunctionalTestBase {

    private static UUID customerId;
    private static UUID linkedBankId;
    private static UUID secondLinkedBankId;

    @BeforeAll
    static void init() {
        customerId = UUID.randomUUID();
    }

    @BeforeEach
    void setupStubs() {
        BankingPlatformStubs.stubRoutingLookup("021000021", "JPMorgan Chase");
        BankingPlatformStubs.stubRoutingLookup("322271627", "Bank of America");
        BankingPlatformStubs.stubEmailNotification();
    }

    @Test
    @Order(1)
    @DisplayName("1. Search bank directory by name → results returned")
    void searchBankDirectory() {
        given(requestSpec)
                .queryParam("name", "Chase")
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/banks/directory/search")
                .then()
                .statusCode(200);
    }

    @Test
    @Order(2)
    @DisplayName("2. Link external bank (micro-deposit) → 201, PENDING_VERIFICATION")
    void linkBankAccount() {
        Response response = given(requestSpec)
                .body(TestDataFactory.linkBankRequest(customerId))
                .when()
                .post("/api/v1/banks/link")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("linkStatus", equalTo("PENDING_VERIFICATION"))
                .body("routingNumber", equalTo("021000021"))
                .body("verificationMethod", equalTo("MICRO_DEPOSIT"))
                .extract().response();

        linkedBankId = UUID.fromString(response.jsonPath().getString("id"));
        assertThat(linkedBankId).isNotNull();
        System.out.println("[BankMgmt] Linked bank: " + linkedBankId);
    }

    @Test
    @Order(3)
    @DisplayName("3. Get linked banks for customer → 1 bank returned")
    void getLinkedBanks() {
        assertThat(customerId).isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/banks/{customerId}", customerId)
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("[0].linkStatus", equalTo("PENDING_VERIFICATION"));
    }

    @Test
    @Order(4)
    @DisplayName("4. Get linked bank by ID → 200 OK")
    void getLinkedBankById() {
        assertThat(linkedBankId).isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/banks/linked/{id}", linkedBankId)
                .then()
                .statusCode(200)
                .body("id", equalTo(linkedBankId.toString()))
                .body("routingNumber", equalTo("021000021"));
    }

    @Test
    @Order(5)
    @DisplayName("5. Verify micro-deposits (correct amounts) → VERIFIED")
    void verifyMicroDepositsSuccess() {
        assertThat(linkedBankId).isNotNull();

        given(requestSpec)
                .body(TestDataFactory.verifyMicroDepositsRequest(
                        new BigDecimal("0.32"), new BigDecimal("0.45")))
                .when()
                .post("/api/v1/banks/{id}/verify", linkedBankId)
                .then()
                .statusCode(200)
                .body("linkStatus", equalTo("VERIFIED"));
    }

    @Test
    @Order(6)
    @DisplayName("6. Set verified bank as primary → isPrimary=true")
    void setBankAsPrimary() {
        assertThat(linkedBankId).isNotNull();

        given(requestSpec)
                .when()
                .post("/api/v1/banks/{id}/primary", linkedBankId)
                .then()
                .statusCode(200)
                .body("primary", equalTo(true));
    }

    @Test
    @Order(7)
    @DisplayName("7. Link second bank (instant verification) → 201 Created")
    void linkSecondBank() {
        Map<String, Object> request = new java.util.HashMap<>(TestDataFactory.linkBankRequest(customerId));
        request.put("routingNumber", "322271627");
        request.put("verificationMethod", "INSTANT_VERIFICATION");
        request.put("nickname", "My BofA Savings");

        Response response = given(requestSpec)
                .body(request)
                .when()
                .post("/api/v1/banks/link")
                .then()
                .statusCode(201)
                .body("routingNumber", equalTo("322271627"))
                .extract().response();

        secondLinkedBankId = UUID.fromString(response.jsonPath().getString("id"));
        assertThat(secondLinkedBankId).isNotNull();
    }

    @Test
    @Order(8)
    @DisplayName("8. Get updated bank list → 2 banks returned")
    void getUpdatedBankList() {
        given(requestSpec)
                .when()
                .get("/api/v1/banks/{customerId}", customerId)
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(2)));
    }

    @Test
    @Order(9)
    @DisplayName("9. Verify with wrong amounts → 422 Unprocessable Entity")
    void verifyMicroDepositsWrongAmounts() {
        assertThat(secondLinkedBankId).isNotNull();

        given(requestSpec)
                .body(TestDataFactory.verifyMicroDepositsRequest(
                        new BigDecimal("0.01"), new BigDecimal("0.02")))
                .when()
                .post("/api/v1/banks/{id}/verify", secondLinkedBankId)
                .then()
                .statusCode(422);
    }

    @Test
    @Order(10)
    @DisplayName("10. Remove second linked bank → 204 No Content")
    void removeLinkedBank() {
        assertThat(secondLinkedBankId).isNotNull();

        given(requestSpec)
                .when()
                .delete("/api/v1/banks/{id}", secondLinkedBankId)
                .then()
                .statusCode(204);
    }

    @Test
    @Order(11)
    @DisplayName("11. Get bank list after removal → 1 bank remaining")
    void getBankListAfterRemoval() {
        given(requestSpec)
                .when()
                .get("/api/v1/banks/{customerId}", customerId)
                .then()
                .statusCode(200)
                .body("$", hasSize(1));
    }

    @Test
    @Order(12)
    @DisplayName("12. Linked bank not found → 404 Not Found")
    void linkedBankNotFound() {
        given(requestSpec)
                .when()
                .get("/api/v1/banks/linked/{id}", UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    @Order(13)
    @DisplayName("13. Link bank with missing routingNumber → 400 Bad Request")
    void linkBankMissingRoutingNumber() {
        given(requestSpec)
                .body(Map.of(
                        "customerId", customerId.toString(),
                        "accountNumber", "1234567890",
                        "accountType", "CHECKING",
                        "verificationMethod", "MICRO_DEPOSIT"
                ))
                .when()
                .post("/api/v1/banks/link")
                .then()
                .statusCode(400);
    }
}

