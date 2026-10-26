package dev.bidu.core.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Injectable time source so NLU, planning and scheduling are testable without wall-clock time. */
interface Clock {
    val zone: ZoneId
    fun now(): Instant
    fun nowZoned(): ZonedDateTime = now().atZone(zone)
}

class SystemClock(override val zone: ZoneId = ZoneId.systemDefault()) : Clock {
    override fun now(): Instant = Instant.now()
}

/** Fixed clock for tests; [advanceBy] moves it forward deterministically. */
class FixedClock(private var current: Instant, override val zone: ZoneId = ZoneId.of("Asia/Kolkata")) : Clock {
    override fun now(): Instant = current
    fun advanceBy(millis: Long) {
        current = current.plusMillis(millis)
    }
    fun set(instant: Instant) {
        current = instant
    }
}
