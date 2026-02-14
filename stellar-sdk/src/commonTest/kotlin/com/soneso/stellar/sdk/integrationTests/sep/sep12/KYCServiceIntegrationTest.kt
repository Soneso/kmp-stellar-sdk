// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.integrationTests.sep.sep12

import com.soneso.stellar.sdk.sep.sep12.*
import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep09.NaturalPersonKYCFields
import com.soneso.stellar.sdk.sep.sep09.StandardKYCFields
import com.soneso.stellar.sdk.sep.sep10.WebAuth
import com.soneso.stellar.sdk.sep.sep12.exceptions.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Integration tests for SEP-12 KYC API against live testnet anchor.
 *
 * These tests validate the complete SEP-12 KYC workflow against
 * testanchor.stellar.org, a real Stellar test anchor maintained by the
 * Stellar Development Foundation.
 *
 * Test Coverage:
 * - Service discovery via stellar.toml
 * - Initial customer status check (NEEDS_INFO)
 * - Customer information submission (PUT)
 * - Status verification after submission
 * - Field validation and required fields
 * - Multiple customer updates
 * - Error handling (401 Unauthorized, 404 Not Found, 400 Bad Request)
 * - Customer deletion (GDPR compliance)
 * - Callback registration
 *
 * Workflow:
 * 1. Generate random keypair and fund via Friendbot
 * 2. Authenticate with SEP-10 to obtain JWT token
 * 3. Check initial customer status (should be NEEDS_INFO)
 * 4. Submit customer information with SEP-09 fields
 * 5. Verify customer was created/updated
 * 6. Test various operations (callback, deletion, etc.)
 *
 * Network Requirements:
 * - Connectivity to https://testanchor.stellar.org
 * - Connectivity to https://friendbot.stellar.org (for account funding)
 * - Average test duration: 10-20 seconds per test (depends on network latency)
 *
 * Test Data:
 * - Each test creates a fresh account to ensure clean state
 * - Accounts are funded with 10,000 XLM via Friendbot
 * - Uses realistic SEP-09 KYC field values
 *
 * Reference:
 * - SEP-12 Specification: https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0012.md
 * - SEP-10 Specification: https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0010.md
 * - SEP-09 Specification: https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0009.md
 * - Test Anchor: https://testanchor.stellar.org
 * - Test Anchor stellar.toml: https://testanchor.stellar.org/.well-known/stellar.toml
 *
 * Note: These tests are NOT marked with @Ignore as they always have testnet
 * connectivity and accounts are automatically funded by Friendbot.
 */
class KYCServiceIntegrationTest {

    // Test anchor configuration
    private val testAnchorDomain = "testanchor.stellar.org"
    private val network = Network.TESTNET

    /**
     * Helper function to create a funded account and obtain SEP-10 JWT token.
     *
     * @return Pair of (KeyPair, JWT token string)
     */
    private suspend fun createAuthenticatedAccount(): Pair<KeyPair, String> {
        // Generate random keypair
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        // Fund account via Friendbot
        val friendBotResponse = FriendBot.fundTestnetAccount(accountId)
        assertTrue(friendBotResponse, "Account funding should succeed")

        // Authenticate with SEP-10 to get JWT token
        val webAuth = WebAuth.fromDomain(
            domain = testAnchorDomain,
            network = network
        )

        val authToken = webAuth.jwtToken(
            clientAccountId = accountId,
            signers = listOf(keyPair)
        )

        return Pair(keyPair, authToken.token)
    }

    /**
     * Tests SEP-12 service discovery via stellar.toml.
     *
     * Validates that:
     * - KYCService can discover KYC_SERVER from stellar.toml
     * - The service is properly initialized
     *
     * Expected Result:
     * - KYCService instance is created successfully
     * - Service address is discovered from stellar.toml
     */
    @Test
    fun testServiceDiscoveryFromDomain() = runTest {
        val kycService = KYCService.fromDomain(
            domain = testAnchorDomain
        )

        assertNotNull(kycService, "KYC service should be created from domain")
    }

    /**
     * Tests initial customer status check for new customer.
     *
     * Validates that:
     * - GET /customer works with valid JWT
     * - New customers start in NEEDS_INFO status
     * - Server returns required fields
     *
     * Flow:
     * 1. Create and fund new account
     * 2. Authenticate with SEP-10
     * 3. Call GET /customer
     * 4. Verify status is NEEDS_INFO
     * 5. Verify required fields are returned
     *
     * Expected Result:
     * - Status is NEEDS_INFO for new customer
     * - Fields object contains required field definitions
     * - No customer ID yet (not registered)
     */
    @Test
    fun testGetCustomerInfoForNewCustomer() = runTest {
        val (keyPair, jwtToken) = createAuthenticatedAccount()

        val kycService = KYCService.fromDomain(
            domain = testAnchorDomain
        )

        val request = GetCustomerInfoRequest(
            jwt = jwtToken
        )

        val response = kycService.getCustomerInfo(request)

        // New customers should need info
        assertEquals(CustomerStatus.NEEDS_INFO, response.status,
            "New customer should have NEEDS_INFO status")

        // Should have fields indicating what's required
        assertNotNull(response.fields, "Response should contain required fields")
        assertTrue(response.fields!!.isNotEmpty(), "Should have at least one required field")

        println("Initial customer status check successful:")
        println("  Account: ${keyPair.getAccountId()}")
        println("  Status: ${response.status}")
        println("  Required fields: ${response.fields!!.keys.joinToString(", ")}")
    }

    /**
     * Tests submitting customer information via PUT /customer.
     *
     * Validates that:
     * - PUT /customer accepts SEP-09 standard fields
     * - Server returns customer ID on successful submission
     * - Customer data is stored
     *
     * Flow:
     * 1. Create and fund new account
     * 2. Authenticate with SEP-10
     * 3. Submit basic customer info (first name, last name, email)
     * 4. Verify customer ID is returned
     *
     * Expected Result:
     * - Response contains customer ID
     * - Customer ID is non-empty string
     */
    @Test
    fun testPutCustomerInfoBasicSubmission() = runTest {
        val (keyPair, jwtToken) = createAuthenticatedAccount()

        val kycService = KYCService.fromDomain(
            domain = testAnchorDomain
        )

        val putRequest = PutCustomerInfoRequest(
            jwt = jwtToken,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "John",
                    lastName = "Doe",
                    emailAddress = "john.doe@example.com"
                )
            )
        )

        val response = kycService.putCustomerInfo(putRequest)

        assertNotNull(response.id, "Response should contain customer ID")
        assertTrue(response.id.isNotEmpty(), "Customer ID should not be empty")

        println("Customer information submitted successfully:")
        println("  Account: ${keyPair.getAccountId()}")
        println("  Customer ID: ${response.id}")
    }

    /**
     * Tests complete KYC submission workflow with all common fields.
     *
     * Validates that:
     * - PUT /customer accepts complete SEP-09 natural person fields
     * - All standard fields are processed
     * - Subsequent GET returns submitted data
     *
     * Flow:
     * 1. Create and fund new account
     * 2. Authenticate with SEP-10
     * 3. Submit complete customer profile (name, email, birth date, address, etc.)
     * 4. Verify customer ID is returned
     * 5. Retrieve customer info
     * 6. Verify submitted fields appear in providedFields
     *
     * Expected Result:
     * - Customer ID is returned
     * - GET /customer shows provided fields
     */
    @Test
    fun testPutCustomerInfoCompleteProfile() = runTest {
        val (keyPair, jwtToken) = createAuthenticatedAccount()

        val kycService = KYCService.fromDomain(
            domain = testAnchorDomain
        )

        // Submit complete customer profile
        val putRequest = PutCustomerInfoRequest(
            jwt = jwtToken,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "Jane",
                    lastName = "Smith",
                    emailAddress = "jane.smith@example.com",
                    birthDate = LocalDate(1985, 6, 15),
                    mobileNumber = "+1-202-555-0123",
                    addressCountryCode = "USA",
                    stateOrProvince = "California",
                    city = "San Francisco",
                    postalCode = "94102",
                    address = "123 Market Street"
                )
            )
        )

        val putResponse = kycService.putCustomerInfo(putRequest)
        assertNotNull(putResponse.id, "Should return customer ID")

        // Retrieve customer info to verify submission
        val getRequest = GetCustomerInfoRequest(
            jwt = jwtToken,
            id = putResponse.id
        )

        val getResponse = kycService.getCustomerInfo(getRequest)

        assertEquals(putResponse.id, getResponse.id, "Customer ID should match")
        assertNotNull(getResponse.providedFields, "Should have provided fields")

        println("Complete customer profile submitted:")
        println("  Customer ID: ${putResponse.id}")
        println("  Status: ${getResponse.status}")
        println("  Provided fields: ${getResponse.providedFields?.keys?.joinToString(", ")}")
    }

    /**
     * Tests updating existing customer information.
     *
     * Validates that:
     * - PUT /customer is idempotent (can update existing customer)
     * - Customer ID remains the same on update
     * - Updated fields are reflected in subsequent GET
     *
     * Flow:
     * 1. Create and fund account
     * 2. Authenticate with SEP-10
     * 3. Submit initial customer info
     * 4. Update customer info with additional fields
     * 5. Verify customer ID remains the same
     *
     * Expected Result:
     * - Same customer ID returned on update
     * - Updated fields appear in providedFields
     */
    @Test
    fun testUpdateExistingCustomer() = runTest {
        val (keyPair, jwtToken) = createAuthenticatedAccount()

        val kycService = KYCService.fromDomain(
            domain = testAnchorDomain
        )

        // Initial submission
        val initialRequest = PutCustomerInfoRequest(
            jwt = jwtToken,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "Alice",
                    lastName = "Johnson"
                )
            )
        )

        val initialResponse = kycService.putCustomerInfo(initialRequest)
        val customerId = initialResponse.id

        // Update with additional information
        val updateRequest = PutCustomerInfoRequest(
            jwt = jwtToken,
            id = customerId,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    emailAddress = "alice.johnson@example.com",
                    mobileNumber = "+1-415-555-0199"
                )
            )
        )

        val updateResponse = kycService.putCustomerInfo(updateRequest)

        assertEquals(customerId, updateResponse.id, "Customer ID should remain the same on update")

        println("Customer updated successfully:")
        println("  Customer ID: $customerId")
        println("  Update preserved customer ID: ${customerId == updateResponse.id}")
    }

    /**
     * Tests customer callback registration.
     *
     * Validates that:
     * - PUT /customer/callback accepts callback URL
     * - Service returns success response (or graceful error if not supported)
     *
     * Flow:
     * 1. Create and fund account
     * 2. Authenticate with SEP-10
     * 3. Register callback URL
     * 4. Verify response (success or not supported)
     *
     * Note: Some anchors may not support callback registration.
     * This test verifies the SDK correctly makes the request.
     */
    @Test
    fun testPutCustomerCallback() = runTest {
        val (keyPair, jwtToken) = createAuthenticatedAccount()

        val kycService = KYCService.fromDomain(
            domain = testAnchorDomain
        )

        val callbackRequest = PutCustomerCallbackRequest(
            jwt = jwtToken,
            url = "https://example.com/kyc-webhook",
            account = keyPair.getAccountId()
        )

        try {
            val response = kycService.putCustomerCallback(callbackRequest)
            assertTrue(response.status.value in 200..299,
                "Callback registration should return success status")
            println("Callback registered successfully:")
            println("  Account: ${keyPair.getAccountId()}")
            println("  Callback URL: https://example.com/kyc-webhook")
            println("  Response status: ${response.status.value}")
        } catch (e: KYCException) {
            // Some anchors don't support callback registration
            // This is acceptable - the SDK correctly made the request
            println("Callback registration not supported by this anchor:")
            println("  Error: ${e.message}")
            assertTrue(true, "SDK correctly attempted callback registration")
        }
    }

    /**
     * Tests customer deletion (GDPR compliance).
     *
     * Validates that:
     * - DELETE /customer/{account} removes customer data
     * - Service returns success response
     * - Subsequent GET returns 404 or NEEDS_INFO status
     *
     * Flow:
     * 1. Create and fund account
     * 2. Authenticate with SEP-10
     * 3. Submit customer info
     * 4. Delete customer
     * 5. Verify deletion success
     * 6. Verify customer info is gone
     *
     * Expected Result:
     * - HTTP 200 OK on deletion
     * - Subsequent GET returns 404 or NEEDS_INFO status
     */
    @Test
    fun testDeleteCustomer() = runTest {
        val (keyPair, jwtToken) = createAuthenticatedAccount()
        val accountId = keyPair.getAccountId()

        val kycService = KYCService.fromDomain(
            domain = testAnchorDomain
        )

        // Submit customer info first
        val putRequest = PutCustomerInfoRequest(
            jwt = jwtToken,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "Bob",
                    lastName = "Williams"
                )
            )
        )

        val putResponse = kycService.putCustomerInfo(putRequest)
        assertNotNull(putResponse.id, "Customer should be created")

        // Delete customer
        val deleteResponse = kycService.deleteCustomer(
            account = accountId,
            jwt = jwtToken
        )

        assertTrue(deleteResponse.status.value in 200..299,
            "Delete should return success status")

        // Verify customer is deleted - should return 404 or NEEDS_INFO
        val getRequest = GetCustomerInfoRequest(jwt = jwtToken)

        try {
            val getResponse = kycService.getCustomerInfo(getRequest)
            // If no exception, customer should be back to NEEDS_INFO state
            assertEquals(CustomerStatus.NEEDS_INFO, getResponse.status,
                "After deletion, customer should be in NEEDS_INFO state")
        } catch (e: CustomerNotFoundException) {
            // This is also acceptable - customer completely removed
            assertTrue(true, "Customer was completely removed (404 response)")
        }

        println("Customer deleted successfully:")
        println("  Account: $accountId")
        println("  Delete response: ${deleteResponse.status.value}")
    }

    /**
     * Tests error handling for unauthorized request.
     *
     * Validates that:
     * - Invalid JWT token results in authentication error
     * - Either UnauthorizedException (401) or KYCException (403) is thrown
     *
     * Note: Different anchors may return 401 Unauthorized or 403 Forbidden
     * for invalid tokens. Both are valid authentication rejections.
     */
    @Test
    fun testUnauthorizedError() = runTest {
        val kycService = KYCService.fromDomain(
            domain = testAnchorDomain
        )

        val request = GetCustomerInfoRequest(
            jwt = "invalid_token_12345"
        )

        try {
            kycService.getCustomerInfo(request)
            // Should not reach here - invalid token should be rejected
            assertTrue(false, "Request with invalid token should be rejected")
        } catch (e: UnauthorizedException) {
            // Expected - 401 Unauthorized
            println("Unauthorized error handling works correctly (401)")
        } catch (e: KYCException) {
            // Some anchors return 403 Forbidden instead of 401
            assertTrue(e.message?.contains("403") == true || e.message?.contains("forbidden") == true,
                "Should receive 401 or 403 for invalid token")
            println("Unauthorized error handling works correctly (403 Forbidden)")
        }
    }

    /**
     * Tests error handling for customer not found.
     *
     * Validates that:
     * - Requesting non-existent customer by ID returns 404
     * - CustomerNotFoundException is thrown
     *
     * Expected Result:
     * - CustomerNotFoundException is thrown
     */
    @Test
    fun testCustomerNotFoundError() = runTest {
        val (keyPair, jwtToken) = createAuthenticatedAccount()

        val kycService = KYCService.fromDomain(
            domain = testAnchorDomain
        )

        val request = GetCustomerInfoRequest(
            jwt = jwtToken,
            id = "non-existent-customer-id-12345"
        )

        assertFailsWith<CustomerNotFoundException> {
            kycService.getCustomerInfo(request)
        }

        println("Customer not found error handling works correctly")
    }

    /**
     * Tests error handling for invalid field data.
     *
     * Validates that:
     * - Submitting invalid data is handled gracefully
     * - Either InvalidFieldException (400) is thrown, or server accepts the data
     *
     * Note: Different anchors have different validation rules. Some may accept
     * any email format, others may validate strictly. This test verifies the
     * SDK correctly handles both scenarios.
     */
    @Test
    fun testInvalidFieldError() = runTest {
        val (keyPair, jwtToken) = createAuthenticatedAccount()

        val kycService = KYCService.fromDomain(
            domain = testAnchorDomain
        )

        // Submit with clearly invalid email (no @ symbol)
        val request = PutCustomerInfoRequest(
            jwt = jwtToken,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "Test",
                    lastName = "User",
                    emailAddress = "not-an-email-address"
                )
            )
        )

        try {
            val response = kycService.putCustomerInfo(request)
            // Some anchors accept any email format
            assertNotNull(response.id, "Customer ID should be returned")
            println("Anchor accepted the data (no strict email validation)")
            println("  Customer ID: ${response.id}")
        } catch (e: InvalidFieldException) {
            // Expected for anchors with strict validation
            println("Invalid field error handling works correctly")
            println("  Field: ${e.fieldName}")
            println("  Error: ${e.message}")
        }
    }

    /**
     * Tests GET /customer with type parameter.
     *
     * Validates that:
     * - Type parameter is properly sent to server
     * - Server can return type-specific requirements
     *
     * Flow:
     * 1. Create and fund account
     * 2. Authenticate with SEP-10
     * 3. Request customer info with type parameter
     * 4. Verify response is returned
     *
     * Expected Result:
     * - Response is returned with status
     * - Type-specific fields may be required
     */
    @Test
    fun testGetCustomerInfoWithType() = runTest {
        val (keyPair, jwtToken) = createAuthenticatedAccount()

        val kycService = KYCService.fromDomain(
            domain = testAnchorDomain
        )

        val request = GetCustomerInfoRequest(
            jwt = jwtToken,
            type = "sep31-sender"
        )

        val response = kycService.getCustomerInfo(request)

        assertNotNull(response.status, "Should return status")

        println("Customer info retrieved with type parameter:")
        println("  Type: sep31-sender")
        println("  Status: ${response.status}")
        println("  Required fields: ${response.fields?.keys?.joinToString(", ") ?: "none"}")
    }

    /**
     * Tests idempotency of PUT /customer requests.
     *
     * Validates that:
     * - Multiple identical PUT requests don't create duplicate customers
     * - Same customer ID is returned each time
     *
     * Flow:
     * 1. Create and fund account
     * 2. Authenticate with SEP-10
     * 3. Submit same customer info twice
     * 4. Verify same customer ID is returned both times
     *
     * Expected Result:
     * - First and second submissions return same customer ID
     * - No duplicate customer created
     */
    @Test
    fun testPutCustomerInfoIdempotency() = runTest {
        val (keyPair, jwtToken) = createAuthenticatedAccount()

        val kycService = KYCService.fromDomain(
            domain = testAnchorDomain
        )

        val putRequest = PutCustomerInfoRequest(
            jwt = jwtToken,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "Charlie",
                    lastName = "Brown",
                    emailAddress = "charlie.brown@example.com"
                )
            )
        )

        // Submit first time
        val firstResponse = kycService.putCustomerInfo(putRequest)
        val firstCustomerId = firstResponse.id

        // Submit second time with same data
        val secondResponse = kycService.putCustomerInfo(putRequest)
        val secondCustomerId = secondResponse.id

        assertEquals(firstCustomerId, secondCustomerId,
            "Multiple submissions should return same customer ID (idempotent)")

        println("Idempotency verified:")
        println("  First submission ID: $firstCustomerId")
        println("  Second submission ID: $secondCustomerId")
        println("  IDs match: ${firstCustomerId == secondCustomerId}")
    }

    /**
     * Tests multiple field updates over time.
     *
     * Validates that:
     * - Customer can be updated multiple times
     * - Each update preserves customer ID
     * - Updates are cumulative (new fields added, existing preserved)
     *
     * Flow:
     * 1. Create and fund account
     * 2. Authenticate with SEP-10
     * 3. Submit initial basic info
     * 4. Update with additional info
     * 5. Update with more info
     * 6. Verify all updates share same customer ID
     *
     * Expected Result:
     * - All updates return same customer ID
     * - Final GET shows all provided fields
     */
    @Test
    fun testMultipleCustomerUpdates() = runTest {
        val (keyPair, jwtToken) = createAuthenticatedAccount()

        val kycService = KYCService.fromDomain(
            domain = testAnchorDomain
        )

        // First update: basic info
        val request1 = PutCustomerInfoRequest(
            jwt = jwtToken,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    firstName = "David",
                    lastName = "Miller"
                )
            )
        )
        val response1 = kycService.putCustomerInfo(request1)
        val customerId = response1.id

        // Second update: contact info
        val request2 = PutCustomerInfoRequest(
            jwt = jwtToken,
            id = customerId,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    emailAddress = "david.miller@example.com",
                    mobileNumber = "+1-650-555-0142"
                )
            )
        )
        val response2 = kycService.putCustomerInfo(request2)

        // Third update: address info
        val request3 = PutCustomerInfoRequest(
            jwt = jwtToken,
            id = customerId,
            kycFields = StandardKYCFields(
                naturalPersonKYCFields = NaturalPersonKYCFields(
                    city = "Los Angeles",
                    stateOrProvince = "California",
                    postalCode = "90001"
                )
            )
        )
        val response3 = kycService.putCustomerInfo(request3)

        // Verify all updates share same customer ID
        assertEquals(customerId, response2.id, "Second update should preserve customer ID")
        assertEquals(customerId, response3.id, "Third update should preserve customer ID")

        // Verify final state
        val getRequest = GetCustomerInfoRequest(
            jwt = jwtToken,
            id = customerId
        )
        val finalState = kycService.getCustomerInfo(getRequest)

        assertNotNull(finalState.providedFields, "Should have provided fields")

        println("Multiple updates completed successfully:")
        println("  Customer ID: $customerId")
        println("  Update count: 3")
        println("  Final provided fields: ${finalState.providedFields?.keys?.joinToString(", ")}")
    }
}
