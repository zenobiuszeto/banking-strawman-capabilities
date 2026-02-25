package com.banking.platform.functional.journey;

import com.banking.platform.functional.config.FunctionalTestBase;
import com.banking.platform.functional.util.TestDataFactory;
import com.banking.platform.functional.wiremock.BankingPlatformStubs;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Functional test: Full Customer Onboarding Journey
 *
 * Scenario:
 *  1. Submit new application
 *  2. Retrieve the application by ID
 *  3. List applications by status
 *  4. Upload a supporting document
 *  5. Progress status: SUBMITTED → UNDER_REVIEW → KYC_PENDING → KYC_APPROVED → APPROVED
 *  6. Attempt duplicate email → 409 Conflict
 *  7. Attempt invalid request → 400 Bad Request
 */
@DisplayName("Journey: Customer Onboarding")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OnboardingJourneyTest extends FunctionalTestBase {

    private static UUID applicationId;
    private static String applicationEmail;

    @BeforeEach
    void setupStubs() {
        BankingPlatformStubs.stubEmailNotification();
        BankingPlatformStubs.stubKycApproved("alice.mercer");
    }

    @Test
    @Order(1)
    @DisplayName("1. Submit valid application → 201 Created")
    void submitApplication() {
        Map<String, Object> request = TestDataFactory.createApplicationRequest("CHECKING");
        applicationEmail = (String) request.get("email");

        Response response = given(requestSpec)
                .body(request)
                .when()
                .post("/api/v1/onboarding/applications")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("email", equalTo(applicationEmail))
                .body("status", equalTo("SUBMITTED"))
                .body("firstName", equalTo("Alice"))
                .body("lastName", equalTo("Mercer"))
                .extract().response();

        applicationId = UUID.fromString(response.jsonPath().getString("id"));
        assertThat(applicationId).isNotNull();
        System.out.println("[Onboarding] Created application: " + applicationId);
    }

    @Test
    @Order(2)
    @DisplayName("2. Retrieve application by ID → 200 OK")
    void getApplicationById() {
        assertThat(applicationId).as("applicationId must be set by order-1 test").isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/onboarding/applications/{id}", applicationId)
                .then()
                .statusCode(200)
                .body("id", equalTo(applicationId.toString()))
                .body("status", equalTo("SUBMITTED"))
                .body("email", equalTo(applicationEmail));
    }

    @Test
    @Order(3)
    @DisplayName("3. List applications by SUBMITTED status → paginated result")
    void listApplicationsByStatus() {
        given(requestSpec)
                .queryParam("status", "SUBMITTED")
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/onboarding/applications")
                .then()
                .statusCode(200)
                .body("content", not(empty()))
                .body("page", equalTo(0))
                .body("size", equalTo(10));
    }

    @Test
    @Order(4)
    @DisplayName("4. Upload document to application → 201 Created")
    void uploadDocument() {
        assertThat(applicationId).isNotNull();

        Map<String, Object> docRequest = Map.of(
                "type", "GOVERNMENT_ID",
                "documentUrl", "https://docs.testbank.com/kyc/" + applicationId + "/id.pdf",
                "description", "Driver's license scan"
        );

        given(requestSpec)
                .body(docRequest)
                .when()
                .post("/api/v1/onboarding/applications/{id}/documents", applicationId)
                .then()
                .statusCode(201);
    }

    @Test
    @Order(5)
    @DisplayName("5. Retrieve uploaded documents → non-empty list")
    void getApplicationDocuments() {
        assertThat(applicationId).isNotNull();

        given(requestSpec)
                .when()
                .get("/api/v1/onboarding/applications/{id}/documents", applicationId)
                .then()
                .statusCode(200)
                .body("$", not(empty()));
    }

    @Test
    @Order(6)
    @DisplayName("6. Transition: SUBMITTED → UNDER_REVIEW")
    void transitionToUnderReview() {
        assertThat(applicationId).isNotNull();

        given(requestSpec)
                .body(TestDataFactory.updateStatusRequest("UNDER_REVIEW", "reviewer@bank.com"))
                .when()
                .patch("/api/v1/onboarding/applications/{id}/status", applicationId)
                .then()
                .statusCode(200)
                .body("status", equalTo("UNDER_REVIEW"));
    }

    @Test
    @Order(7)
    @DisplayName("7. Transition: UNDER_REVIEW → KYC_PENDING")
    void transitionToKycPending() {
        assertThat(applicationId).isNotNull();

        given(requestSpec)
                .body(TestDataFactory.updateStatusRequest("KYC_PENDING", "reviewer@bank.com"))
                .when()
                .patch("/api/v1/onboarding/applications/{id}/status", applicationId)
                .then()
                .statusCode(200)
                .body("status", equalTo("KYC_PENDING"));
    }

    @Test
    @Order(8)
    @DisplayName("8. Transition: KYC_PENDING → KYC_APPROVED")
    void transitionToKycApproved() {
        assertThat(applicationId).isNotNull();

        given(requestSpec)
                .body(TestDataFactory.updateStatusRequest("KYC_APPROVED", "kyc-system@bank.com"))
                .when()
                .patch("/api/v1/onboarding/applications/{id}/status", applicationId)
                .then()
                .statusCode(200)
                .body("status", equalTo("KYC_APPROVED"));
    }

    @Test
    @Order(9)
    @DisplayName("9. Transition: KYC_APPROVED → APPROVED")
    void transitionToApproved() {
        assertThat(applicationId).isNotNull();

        given(requestSpec)
                .body(TestDataFactory.updateStatusRequest("APPROVED", "supervisor@bank.com"))
                .when()
                .patch("/api/v1/onboarding/applications/{id}/status", applicationId)
                .then()
                .statusCode(200)
                .body("status", equalTo("APPROVED"));
    }

    @Test
    @Order(10)
    @DisplayName("10. Duplicate email → 409 Conflict")
    void duplicateEmailRejected() {
        Map<String, Object> duplicate = TestDataFactory.createApplicationRequest("CHECKING");
        // Force same email as the first application
        Map<String, Object> withSameEmail = new java.util.HashMap<>(duplicate);
        withSameEmail.put("email", applicationEmail);

        given(requestSpec)
                .body(withSameEmail)
                .when()
                .post("/api/v1/onboarding/applications")
                .then()
                .statusCode(409);
    }

    @Test
    @Order(11)
    @DisplayName("11. Invalid request (missing required fields) → 400 Bad Request")
    void invalidRequestRejected() {
        Map<String, Object> invalid = Map.of("firstName", "Bob");  // missing all required fields

        given(requestSpec)
                .body(invalid)
                .when()
                .post("/api/v1/onboarding/applications")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(12)
    @DisplayName("12. Application not found → 404 Not Found")
    void applicationNotFound() {
        given(requestSpec)
                .when()
                .get("/api/v1/onboarding/applications/{id}", UUID.randomUUID())
                .then()
                .statusCode(404);
    }
}

