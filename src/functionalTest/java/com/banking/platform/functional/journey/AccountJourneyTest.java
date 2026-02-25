package com.banking.platform.functional.journey;

import com.banking.platform.functional.config.FunctionalTestBase;
import com.banking.platform.functional.util.TestDataFactory;
import com.banking.platform.functional.wiremock.BankingPlatformStubs;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Functional test: Account & Dashboard Journey
 *
 * Scenario:
 *  1. Create a CHECKING account
 *  2. Create a SAVINGS account for the same customer
 *  3. Get account by ID
 *  4. Get account by account number
 *  5. List all accounts for customer
 *  6. Get balance details
 *  7. Get customer dashboard (aggregated view)
 *  8. Update account status (ACTIVE → FROZEN → ACTIVE)
 *  9. List interest charges
 * 10. Account not found → 404
 */
@DisplayName("Journey: Account & Dashboard")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountJourneyTest extends FunctionalTestBase {

    private static UUID customerId;
    private static UUID checkingAccountId;
    private static UUID savingsAccountId;
    private static String checkingAccountNumber;

    @BeforeAll
    static void init() {
        customerId = UUID.randomUUID();
    }

    @BeforeEach
    void setupStubs() {
        BankingPlatformStubs.stubEmailNotification();
    }

    @Test
    @Order(1)
    @DisplayName("1. Create CHECKING account → 201 Created")
    void createCheckingAccount() {
        Response response = given(requestSpec)
                .body(TestDataFactory.createAccountRequest(customerId, "CHECKING", new BigDecimal("5000.00")))
                .when()
                .post("/api/v1/accounts")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("type", equalTo("CHECKING"))
                .body("status", equalTo("ACTIVE"))
                .body("currentBalance", comparesEqualTo(new BigDecimal("5000.00").floatValue()))
                .body("currency", equalTo("USD"))
                .extract().response();

        checkingAccountId = UUID.fromString(response.jsonPath().getString("id"));
        checkingAccountNumber = response.jsonPath().getString("accountNumber");
        assertThat(checkingAccountId).isNotNull();
        assertThat(checkingAccountNumber).isNotBlank();
        System.out.println("[Account] Checking account created: " + checkingAccountId
                + " / " + checkingAccountNumber);
    }

    @Test
    @Order(2)
    @DisplayName("2. Create SAVINGS account for same customer → 201 Created")
    void createSavingsAccount() {
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
        assertThat(savingsAccountId).isNotNull();
        System.out.println("[Account] Savings account created: " + savingsAccountId);
    }

    @Test
    @Order(3)
    @DisplayName("3. Get account by ID → 200 OK with correct data")
    void getAccountById() {
        assertThat(checkingAccountId).isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/accounts/{id}", checkingAccountId)
                .then()
                .statusCode(200)
                .body("id", equalTo(checkingAccountId.toString()))
                .body("type", equalTo("CHECKING"))
                .body("status", equalTo("ACTIVE"));
    }

    @Test
    @Order(4)
    @DisplayName("4. Get account by account number → 200 OK")
    void getAccountByNumber() {
        assertThat(checkingAccountNumber).isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/accounts/number/{number}", checkingAccountNumber)
                .then()
                .statusCode(200)
                .body("accountNumber", equalTo(checkingAccountNumber));
    }

    @Test
    @Order(5)
    @DisplayName("5. List all accounts for customer → 2 accounts returned")
    void listCustomerAccounts() {
        assertThat(customerId).isNotNull();

        Response response = given(requestSpec)
                .when()
                .get("/api/v1/accounts/customer/{customerId}", customerId)
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(2)))
                .extract().response();

        List<String> types = response.jsonPath().getList("type");
        assertThat(types).contains("CHECKING", "SAVINGS");
    }

    @Test
    @Order(6)
    @DisplayName("6. Get balance details → balances present")
    void getBalanceDetails() {
        assertThat(checkingAccountId).isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/accounts/{id}/balance", checkingAccountId)
                .then()
                .statusCode(200)
                .body("currentBalance", notNullValue())
                .body("availableBalance", notNullValue());
    }

    @Test
    @Order(7)
    @DisplayName("7. Get customer dashboard → aggregated summary")
    void getCustomerDashboard() {
        assertThat(customerId).isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/dashboard/{customerId}", customerId)
                .then()
                .statusCode(200)
                .body("customerId", equalTo(customerId.toString()))
                .body("accounts", not(empty()))
                .body("totalBalance", notNullValue());
    }

    @Test
    @Order(8)
    @DisplayName("8. Freeze account → status becomes FROZEN")
    void freezeAccount() {
        assertThat(checkingAccountId).isNotNull();

        given(requestSpec)
                .body(Map.of("status", "FROZEN", "reason", "Suspicious activity"))
                .when()
                .patch("/api/v1/accounts/{id}/status", checkingAccountId)
                .then()
                .statusCode(200)
                .body("status", equalTo("FROZEN"));
    }

    @Test
    @Order(9)
    @DisplayName("9. Reactivate frozen account → status becomes ACTIVE")
    void reactivateAccount() {
        assertThat(checkingAccountId).isNotNull();

        given(requestSpec)
                .body(Map.of("status", "ACTIVE", "reason", "Issue resolved"))
                .when()
                .patch("/api/v1/accounts/{id}/status", checkingAccountId)
                .then()
                .statusCode(200)
                .body("status", equalTo("ACTIVE"));
    }

    @Test
    @Order(10)
    @DisplayName("10. List interest charges → 200 OK (may be empty)")
    void listInterestCharges() {
        assertThat(checkingAccountId).isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/interest-charges/{accountId}", checkingAccountId)
                .then()
                .statusCode(200);
    }

    @Test
    @Order(11)
    @DisplayName("11. Account not found → 404 Not Found")
    void accountNotFound() {
        given(requestSpec)
                .when()
                .get("/api/v1/accounts/{id}", UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    @Order(12)
    @DisplayName("12. Missing customerId → 400 Bad Request")
    void createAccountMissingCustomerId() {
        given(requestSpec)
                .body(Map.of("accountType", "CHECKING", "currency", "USD"))
                .when()
                .post("/api/v1/accounts")
                .then()
                .statusCode(400);
    }
}

