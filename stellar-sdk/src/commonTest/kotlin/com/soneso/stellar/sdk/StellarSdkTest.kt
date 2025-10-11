package com.soneso.stellar.sdk

import kotlin.test.Test
import kotlin.test.assertEquals

class StellarSdkTest {
    @Test
    fun testVersion() {
        assertEquals("0.1.0-SNAPSHOT", StellarSdk.VERSION)
    }
}