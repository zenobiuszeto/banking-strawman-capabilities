package com.banking.platform.functional.journey;

import com.banking.platform.functional.config.FunctionalTestBase;
import com.banking.platform.functional.util.TestDataFactory;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Functional test: General Ledger (Double-Entry Accounting) Journey
 *
 * Scenario:
 *  1. Create ASSET GL account (Cash)
 *  2. Create LIABILITY GL account (Deposits)
 *  3. Create REVENUE GL account (Interest Income)
 *  4. Get chart of accounts
 *  5. Get GL account by code
 *  6. Create a journal entry (DRAFT)
 *  7. Post the journal entry → POSTED
 *  8. Get journal entry by ID
 *  9. Reverse the journal entry → new reversal entry created
 * 10. Get trial balance
 * 11. Create posting rule
 * 12. List posting rules
 * 13. Toggle posting rule active/inactive
 * 14. Unbalanced journal entry → 400
 */
@DisplayName("Journey: Ledger (GL)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LedgerJourneyTest extends FunctionalTestBase {

    private static UUID cashGlAccountId;
    private static UUID depositsGlAccountId;
    private static UUID journalEntryId;
    private static UUID postingRuleId;

    @Test
    @Order(1)
    @DisplayName("1. Create ASSET GL account (1001 - Cash) → 201 Created")
    void createCashGlAccount() {
        Response response = given(requestSpec)
                .body(TestDataFactory.createGlAccountRequest("1001", "Cash", "ASSET"))
                .when()
                .post("/api/v1/ledger/gl-accounts")
                .then()
                .statusCode(201)
                .body("accountCode", equalTo("1001"))
                .body("accountName", equalTo("Cash"))
                .body("accountType", equalTo("ASSET"))
                .body("normalBalance", equalTo("DEBIT"))
                .extract().response();

        cashGlAccountId = UUID.fromString(response.jsonPath().getString("id"));
        assertThat(cashGlAccountId).isNotNull();
        System.out.println("[Ledger] Cash GL: " + cashGlAccountId);
    }

    @Test
    @Order(2)
    @DisplayName("2. Create LIABILITY GL account (2001 - Customer Deposits) → 201 Created")
    void createDepositsGlAccount() {
        Response response = given(requestSpec)
                .body(TestDataFactory.createGlAccountRequest("2001", "Customer Deposits", "LIABILITY"))
                .when()
                .post("/api/v1/ledger/gl-accounts")
                .then()
                .statusCode(201)
                .body("accountCode", equalTo("2001"))
                .body("accountType", equalTo("LIABILITY"))
                .body("normalBalance", equalTo("CREDIT"))
                .extract().response();

        depositsGlAccountId = UUID.fromString(response.jsonPath().getString("id"));
    }

    @Test
    @Order(3)
    @DisplayName("3. Create REVENUE GL account (4001 - Interest Income) → 201 Created")
    void createRevenueGlAccount() {
        given(requestSpec)
                .body(TestDataFactory.createGlAccountRequest("4001", "Interest Income", "REVENUE"))
                .when()
                .post("/api/v1/ledger/gl-accounts")
                .then()
                .statusCode(201)
                .body("accountType", equalTo("REVENUE"));
    }

    @Test
    @Order(4)
    @DisplayName("4. Get chart of accounts → ≥3 accounts returned")
    void getChartOfAccounts() {
        given(requestSpec)
                .when()
                .get("/api/v1/ledger/gl-accounts")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(3)));
    }

    @Test
    @Order(5)
    @DisplayName("5. Get GL account by code → 200 OK")
    void getGlAccountByCode() {
        given(requestSpec)
                .when()
                .get("/api/v1/ledger/gl-accounts/code/1001")
                .then()
                .statusCode(200)
                .body("accountCode", equalTo("1001"))
                .body("accountName", equalTo("Cash"));
    }

    @Test
    @Order(6)
    @DisplayName("6. Create balanced journal entry (DRAFT) → 201 Created")
    void createJournalEntry() {
        Response response = given(requestSpec)
                .body(TestDataFactory.createJournalEntryRequest(
                        "1001", "2001",
                        new BigDecimal("10000.00"),
                        "Customer deposit received"))
                .when()
                .post("/api/v1/ledger/journal-entries")
                .then()
                .statusCode(201)
                .body("status", equalTo("DRAFT"))
                .body("description", equalTo("Customer deposit received"))
                .body("lines", hasSize(2))
                .extract().response();

        journalEntryId = UUID.fromString(response.jsonPath().getString("id"));
        assertThat(journalEntryId).isNotNull();
        System.out.println("[Ledger] Journal entry created: " + journalEntryId);
    }

    @Test
    @Order(7)
    @DisplayName("7. Post journal entry → status POSTED, entry number assigned")
    void postJournalEntry() {
        assertThat(journalEntryId).isNotNull();

        given(requestSpec)
                .when()
                .post("/api/v1/ledger/journal-entries/{id}/post", journalEntryId)
                .then()
                .statusCode(200)
                .body("status", equalTo("POSTED"))
                .body("entryNumber", notNullValue());
    }

    @Test
    @Order(8)
    @DisplayName("8. Get journal entry by ID → 200 OK with lines")
    void getJournalEntry() {
        assertThat(journalEntryId).isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/ledger/journal-entries/{id}", journalEntryId)
                .then()
                .statusCode(200)
                .body("id", equalTo(journalEntryId.toString()))
                .body("status", equalTo("POSTED"))
                .body("lines", hasSize(2));
    }

    @Test
    @Order(9)
    @DisplayName("9. List journal entries with status filter → results returned")
    void listJournalEntries() {
        given(requestSpec)
                .queryParam("status", "POSTED")
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/ledger/journal-entries")
                .then()
                .statusCode(200)
                .body("content.status", everyItem(equalTo("POSTED")));
    }

    @Test
    @Order(10)
    @DisplayName("10. Reverse journal entry → reversal entry created")
    void reverseJournalEntry() {
        assertThat(journalEntryId).isNotNull();

        given(requestSpec)
                .body(java.util.Map.of("reason", "Data correction"))
                .when()
                .post("/api/v1/ledger/journal-entries/{id}/reverse", journalEntryId)
                .then()
                .statusCode(201)
                .body("status", equalTo("POSTED"))
                .body("description", containsString("Reversal"));
    }

    @Test
    @Order(11)
    @DisplayName("11. Get trial balance → all accounts listed with debit/credit totals")
    void getTrialBalance() {
        given(requestSpec)
                .when()
                .get("/api/v1/ledger/trial-balance")
                .then()
                .statusCode(200)
                .body("entries", not(empty()))
                .body("totalDebits", notNullValue())
                .body("totalCredits", notNullValue());
    }

    @Test
    @Order(12)
    @DisplayName("12. Create posting rule → 201 Created")
    void createPostingRule() {
        Response response = given(requestSpec)
                .body(java.util.Map.of(
                        "triggerEvent", "TRANSACTION_DEBIT",
                        "debitAccountCode", "1001",
                        "creditAccountCode", "2001",
                        "description", "Debit transaction posting rule",
                        "active", true
                ))
                .when()
                .post("/api/v1/ledger/posting-rules")
                .then()
                .statusCode(201)
                .body("triggerEvent", equalTo("TRANSACTION_DEBIT"))
                .body("active", equalTo(true))
                .extract().response();

        postingRuleId = UUID.fromString(response.jsonPath().getString("id"));
        assertThat(postingRuleId).isNotNull();
    }

    @Test
    @Order(13)
    @DisplayName("13. List posting rules → ≥1 rule")
    void listPostingRules() {
        given(requestSpec)
                .when()
                .get("/api/v1/ledger/posting-rules")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(14)
    @DisplayName("14. Toggle posting rule inactive → active=false")
    void togglePostingRule() {
        assertThat(postingRuleId).isNotNull();

        given(requestSpec)
                .when()
                .patch("/api/v1/ledger/posting-rules/{id}/toggle", postingRuleId)
                .then()
                .statusCode(200)
                .body("active", equalTo(false));
    }
}

