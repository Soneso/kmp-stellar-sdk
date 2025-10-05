package com.stellar.sdk

/**
 * JVM implementation of currentTimeMillis using System.currentTimeMillis().
 */
internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()
