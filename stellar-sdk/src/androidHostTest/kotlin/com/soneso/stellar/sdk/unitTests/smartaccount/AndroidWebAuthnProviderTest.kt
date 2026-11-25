//
//  AndroidWebAuthnProviderTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import androidx.credentials.GetPublicKeyCredentialOption
import com.soneso.stellar.sdk.smartaccount.AndroidWebAuthnProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidWebAuthnProviderTest {

    @Test
    fun testCreateRegistrationRequest() {
        val requestJson = """{"user":{"name":"alice","displayName":"alice","id":"YQ"},"challenge":"YQ"}"""

        val request = AndroidWebAuthnProvider.createRegistrationRequest(requestJson)

        assertEquals(requestJson, request.requestJson)
        assertNull(request.clientDataHash)
        assertFalse(request.preferImmediatelyAvailableCredentials)
        assertNull(request.origin)
        assertFalse(request.isAutoSelectAllowed)
    }

    @Test
    fun testCreateAuthenticationRequest() {
        val requestJson = """{"challenge":"YQ","rpId":"example.com"}"""

        val request = AndroidWebAuthnProvider.createAuthenticationRequest(requestJson)

        assertEquals(1, request.credentialOptions.size)
        val option = assertIs<GetPublicKeyCredentialOption>(request.credentialOptions.single())
        assertEquals(requestJson, option.requestJson)
        assertNull(option.clientDataHash)
        assertTrue(option.allowedProviders.isEmpty())
        assertNull(request.origin)
        assertFalse(request.preferIdentityDocUi)
        assertNull(request.preferUiBrandingComponentName)
        assertFalse(request.preferImmediatelyAvailableCredentials)
    }
}
