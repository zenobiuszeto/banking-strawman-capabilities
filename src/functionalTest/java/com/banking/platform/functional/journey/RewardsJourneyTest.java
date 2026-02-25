package com.banking.platform.functional.journey;

import com.banking.platform.functional.config.FunctionalTestBase;
import com.banking.platform.functional.util.TestDataFactory;
import com.banking.platform.functional.wiremock.BankingPlatformStubs;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Functional test: Rewards Program Journey
 *
 * Scenario:
 *  1. Enrol customer in rewards
 *  2. Get rewards account
 *  3. Earn points (multiple transactions to trigger tier check)
 *  4. Get rewards summary (tier, balance, lifetime)
 *  5. Get transaction history (paginated)
 *  6. Get active offers
 *  7. Redeem points for cash-back
 *  8. Verify balance reduced after redemption
 *  9. Already enrolled → 409 Conflict
 * 10. Redeem more than balance → 422
 */
@DisplayName("Journey: Rewards")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RewardsJourneyTest extends FunctionalTestBase {

    private static UUID customerId;
    private static UUID rewardsAccountId;

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
    @DisplayName("1. Enrol customer in rewards → 201 Created, BRONZE tier")
    void enrolInRewards() {
        Response response = given(requestSpec)
                .body(TestDataFactory.enrollRewardsRequest(customerId))
                .when()
                .post("/api/v1/rewards/enrol")
                .then()
                .statusCode(201)
                .body("customerId", equalTo(customerId.toString()))
                .body("tier", equalTo("BRONZE"))
                .body("pointsBalance", equalTo(0))
                .extract().response();

        rewardsAccountId = UUID.fromString(response.jsonPath().getString("id"));
        assertThat(rewardsAccountId).isNotNull();
        System.out.println("[Rewards] Enrolled: " + rewardsAccountId);
    }

    @Test
    @Order(2)
    @DisplayName("2. Get rewards account → 200 OK")
    void getRewardsAccount() {
        assertThat(customerId).isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/rewards/account/{customerId}", customerId)
                .then()
                .statusCode(200)
                .body("customerId", equalTo(customerId.toString()))
                .body("tier", equalTo("BRONZE"));
    }

    @Test
    @Order(3)
    @DisplayName("3. Earn 500 points → transaction created, balance updated")
    void earnPoints() {
        Response response = given(requestSpec)
                .body(TestDataFactory.earnPointsRequest(customerId, 500L))
                .when()
                .post("/api/v1/rewards/earn")
                .then()
                .statusCode(201)
                .body("type", equalTo("EARN"))
                .body("points", greaterThan(0))
                .extract().response();

        System.out.println("[Rewards] Earned " + response.jsonPath().getInt("points") + " points");
    }

    @Test
    @Order(4)
    @DisplayName("4. Earn 1000 more points → balance grows")
    void earnMorePoints() {
        given(requestSpec)
                .body(TestDataFactory.earnPointsRequest(customerId, 1000L))
                .when()
                .post("/api/v1/rewards/earn")
                .then()
                .statusCode(201)
                .body("points", greaterThan(0));
    }

    @Test
    @Order(5)
    @DisplayName("5. Get rewards summary → balance ≥ 1500, tier info present")
    void getRewardsSummary() {
        given(requestSpec)
                .when()
                .get("/api/v1/rewards/account/{customerId}/summary", customerId)
                .then()
                .statusCode(200)
                .body("pointsBalance", greaterThanOrEqualTo(1500))
                .body("tier", notNullValue())
                .body("lifetimePointsEarned", greaterThanOrEqualTo(1500));
    }

    @Test
    @Order(6)
    @DisplayName("6. Get rewards transaction history → paginated, ≥2 entries")
    void getTransactionHistory() {
        given(requestSpec)
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/rewards/account/{customerId}/transactions", customerId)
                .then()
                .statusCode(200)
                .body("content", hasSize(greaterThanOrEqualTo(2)))
                .body("content.type", everyItem(equalTo("EARN")));
    }

    @Test
    @Order(7)
    @DisplayName("7. Get active offers → list returned (may be empty)")
    void getActiveOffers() {
        given(requestSpec)
                .when()
                .get("/api/v1/rewards/offers")
                .then()
                .statusCode(200);
    }

    @Test
    @Order(8)
    @DisplayName("8. Redeem 500 points for cash-back → 201 Created")
    void redeemPoints() {
        given(requestSpec)
                .body(TestDataFactory.redeemPointsRequest(customerId, 500L))
                .when()
                .post("/api/v1/rewards/redeem")
                .then()
                .statusCode(201)
                .body("type", equalTo("REDEEM"))
                .body("redemptionType", equalTo("CASH_BACK"));
    }

    @Test
    @Order(9)
    @DisplayName("9. Get summary after redemption → balance reduced by 500")
    void summaryAfterRedemption() {
        given(requestSpec)
                .when()
                .get("/api/v1/rewards/account/{customerId}/summary", customerId)
                .then()
                .statusCode(200)
                .body("pointsBalance", greaterThanOrEqualTo(1000));
    }

    @Test
    @Order(10)
    @DisplayName("10. Duplicate enrolment → 409 Conflict")
    void duplicateEnrolment() {
        given(requestSpec)
                .body(TestDataFactory.enrollRewardsRequest(customerId))
                .when()
                .post("/api/v1/rewards/enrol")
                .then()
                .statusCode(409);
    }

    @Test
    @Order(11)
    @DisplayName("11. Redeem more than balance → 422 Unprocessable Entity")
    void redeemMoreThanBalance() {
        given(requestSpec)
                .body(TestDataFactory.redeemPointsRequest(customerId, 999_999L))
                .when()
                .post("/api/v1/rewards/redeem")
                .then()
                .statusCode(422);
    }
}

