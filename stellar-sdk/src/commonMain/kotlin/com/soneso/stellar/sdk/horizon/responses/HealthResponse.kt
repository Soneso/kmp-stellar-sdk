package com.soneso.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents health response received from the Horizon server.
 *
 * The health endpoint provides information about the current operational status of the Horizon server.
 * It returns three key indicators that determine whether the server is functioning properly:
 * - Database connectivity
 * - Stellar Core availability
 * - Stellar Core synchronization status
 *
 * The actual Horizon API returns:
 * ```json
 * {
 *   "database_connected": true,
 *   "core_up": true,
 *   "core_synced": true
 * }
 * ```
 *
 * A server is considered healthy when all three indicators are true.
 *
 * @property databaseConnected Indicates whether the Horizon database is connected
 * @property coreUp Indicates whether the Stellar Core instance is up and running
 * @property coreSynced Indicates whether the Stellar Core instance is synced with the network
 *
 * @see com.soneso.stellar.sdk.horizon.HorizonServer.health
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/structure/health">Health endpoint documentation</a>
 */
@Serializable
data class HealthResponse(
    @SerialName("database_connected")
    val databaseConnected: Boolean,

    @SerialName("core_up")
    val coreUp: Boolean,

    @SerialName("core_synced")
    val coreSynced: Boolean
) : Response() {

    /**
     * Returns true if the server is healthy (all systems operational).
     *
     * The server is considered healthy when:
     * - Database is connected ([databaseConnected] = true)
     * - Stellar Core is up ([coreUp] = true)
     * - Stellar Core is synced with the network ([coreSynced] = true)
     *
     * @return true if all health indicators are true, false otherwise
     */
    val isHealthy: Boolean
        get() = databaseConnected && coreUp && coreSynced
}
