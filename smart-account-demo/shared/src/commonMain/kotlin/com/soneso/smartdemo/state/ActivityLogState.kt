package com.soneso.smartdemo.state

import androidx.compose.runtime.mutableStateListOf
import kotlinx.datetime.Instant
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

enum class LogLevel { INFO, SUCCESS, ERROR }

@OptIn(ExperimentalTime::class)
data class LogEntry(
    val message: String,
    val level: LogLevel,
    val timestamp: Instant = Clock.System.now()
)

/**
 * Shared activity log state for the demo app.
 * All screens call addEntry() to log operations.
 * The main screen displays the log entries.
 */
object ActivityLogState {
    private const val MAX_ENTRIES = 50

    val entries = mutableStateListOf<LogEntry>()

    fun addEntry(message: String, level: LogLevel = LogLevel.INFO) {
        entries.add(0, LogEntry(message, level))
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(entries.lastIndex)
        }
    }

    fun info(message: String) = addEntry(message, LogLevel.INFO)
    fun success(message: String) = addEntry(message, LogLevel.SUCCESS)
    fun error(message: String) = addEntry(message, LogLevel.ERROR)

    fun clear() = entries.clear()
}
