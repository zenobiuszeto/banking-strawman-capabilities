package com.banking.platform.functional.wiremock;

import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Centralised WireMock stub factory for all banking platform functional tests.
 *
 * Each method registers stubs on the shared WireMock server for a specific
 * downstream or external dependency that the platform calls at runtime:
 *   - OAuth2/Keycloak token introspection
 *   - External KYC verification service
 *   - External notification service (email/SMS)
 *   - Kafka (via REST Proxy stubs for event verification)
 *   - External bank routing lookup
 *   - Payment network responses
 */
public final class BankingPlatformStubs {

    private BankingPlatformStubs() {}

    // ── OAuth2 / JWT ──────────────────────────────────────────────────────────

    /**
     * Stub a valid JWT introspection response for the given subject.
     */
    public static void stubValidJwt(String subject, String... roles) {
        String rolesList = String.join("\",\"", roles);
        stubFor(post(urlEqualTo("/auth/realms/banking/protocol/openid-connect/token/introspect"))
                .willReturn(okJson("""
                        {
                          "active": true,
                          "sub": "%s",
                          "preferred_username": "%s",
                          "realm_access": { "roles": ["%s"] },
                          "exp": 9999999999
                        }
                        """.formatted(subject, subject, rolesList))));
    }

    // ── KYC Service ──────────────────────────────────────────────────────────

    public static void stubKycApproved(String email) {
        stubFor(post(urlEqualTo("/kyc/verify"))
                .withRequestBody(containing(email))
                .willReturn(okJson("""
                        {
                          "status": "APPROVED",
                          "riskScore": 15,
                          "reason": "All checks passed"
                        }
                        """)));
    }

    public static void stubKycRejected(String email) {
        stubFor(post(urlEqualTo("/kyc/verify"))
                .withRequestBody(containing(email))
                .willReturn(okJson("""
                        {
                          "status": "REJECTED",
                          "riskScore": 85,
                          "reason": "Identity could not be verified"
                        }
                        """)));
    }

    // ── Notification Service ──────────────────────────────────────────────────

    public static void stubEmailNotification() {
        stubFor(post(urlEqualTo("/notifications/email"))
                .willReturn(aResponse().withStatus(202).withBody("{\"queued\": true}")));
    }

    public static void stubSmsNotification() {
        stubFor(post(urlEqualTo("/notifications/sms"))
                .willReturn(aResponse().withStatus(202).withBody("{\"queued\": true}")));
    }

    // ── Bank Routing Lookup ───────────────────────────────────────────────────

    public static void stubRoutingLookup(String routingNumber, String bankName) {
        stubFor(get(urlEqualTo("/routing/" + routingNumber))
                .willReturn(okJson("""
                        {
                          "routingNumber": "%s",
                          "bankName": "%s",
                          "city": "New York",
                          "state": "NY",
                          "valid": true
                        }
                        """.formatted(routingNumber, bankName))));
    }

    public static void stubRoutingNotFound(String routingNumber) {
        stubFor(get(urlEqualTo("/routing/" + routingNumber))
                .willReturn(aResponse().withStatus(404)
                        .withBody("{\"error\": \"Routing number not found\"}")));
    }

    // ── Payment Network (ACH/Wire) ────────────────────────────────────────────

    public static void stubPaymentNetworkAchSubmit(String traceNumber) {
        stubFor(post(urlEqualTo("/payment-network/ach/submit"))
                .willReturn(okJson("""
                        {
                          "traceNumber": "%s",
                          "status": "ACCEPTED",
                          "message": "ACH batch accepted for processing"
                        }
                        """.formatted(traceNumber))));
    }

    public static void stubPaymentNetworkWireSubmit(String networkRef) {
        stubFor(post(urlEqualTo("/payment-network/wire/submit"))
                .willReturn(okJson("""
                        {
                          "networkReference": "%s",
                          "status": "ACCEPTED",
                          "estimatedSettlement": "2026-03-01T17:00:00Z"
                        }
                        """.formatted(networkRef))));
    }

    public static void stubPaymentNetworkDown() {
        stubFor(post(urlMatching("/payment-network/.*"))
                .willReturn(aResponse().withStatus(503)
                        .withBody("{\"error\": \"Payment network temporarily unavailable\"}")));
    }

    // ── Kafka REST Proxy (event verification) ─────────────────────────────────

    public static void stubKafkaProducerSuccess(String topic) {
        stubFor(post(urlEqualTo("/kafka/topics/" + topic))
                .willReturn(okJson("""
                        {
                          "offsets": [{"partition": 0, "offset": 1}],
                          "key_schema_id": null,
                          "value_schema_id": null
                        }
                        """)));
    }

    // ── Fraud Detection Service ───────────────────────────────────────────────

    public static void stubFraudCheckClear(String accountId) {
        stubFor(post(urlEqualTo("/fraud/check"))
                .withRequestBody(containing(accountId))
                .willReturn(okJson("""
                        {
                          "riskScore": 5,
                          "decision": "ALLOW",
                          "reason": "Low risk transaction"
                        }
                        """)));
    }

    public static void stubFraudCheckBlock(String accountId) {
        stubFor(post(urlEqualTo("/fraud/check"))
                .withRequestBody(containing(accountId))
                .willReturn(okJson("""
                        {
                          "riskScore": 92,
                          "decision": "BLOCK",
                          "reason": "Suspected fraudulent activity"
                        }
                        """)));
    }
}

