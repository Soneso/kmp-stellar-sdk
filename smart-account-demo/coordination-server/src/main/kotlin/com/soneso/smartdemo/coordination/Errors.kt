package com.soneso.smartdemo.coordination

/**
 * Raised when a client-supplied value fails validation. Maps to HTTP 400.
 */
class ValidationException(override val message: String) : Exception(message)

/**
 * Raised when a referenced request id does not exist. Maps to HTTP 404.
 */
class NotFoundException(override val message: String) : Exception(message)

/**
 * Raised when a state transition is not permitted, e.g. resolving an
 * already-resolved request. Maps to HTTP 409.
 */
class ConflictException(override val message: String) : Exception(message)

/**
 * Raised when configuration cannot be resolved into a runnable state.
 */
class ConfigException(override val message: String) : Exception(message)

/**
 * Raised when a persisted store file cannot be parsed back into requests.
 * Maps to HTTP 400 over the wire; surfaced at startup it aborts the load.
 */
class StoreFormatException(override val message: String) : Exception(message)
