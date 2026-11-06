package org.octopusden.octopus.license.service.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.policy.LicensePolicySource
import org.octopusden.octopus.license.policy.exception.LicenseRegistryUnavailableException
import org.octopusden.octopus.license.policy.impl.parser.LicenseRegistryParser
import org.octopusden.octopus.license.policy.model.LicensePolicy
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class LicensePolicyRefresherTest {
    private val source = FakeLicensePolicySource()
    private val clock = MutableClock()
    private val holder = LicensePolicyHolder()
    private val refresher = LicensePolicyRefresher(source, holder, REFRESH_INTERVAL, clock)

    @Test
    @DisplayName("the first refresh loads the policy into the holder")
    fun firstLoad() {
        refresher.refresh()

        assertEquals("c1", holder.current()?.policy?.commit)
        assertEquals(clock.instant(), holder.current()?.loadedAt)
    }

    @Test
    @DisplayName("a failed first load leaves the holder empty, and the next tick tries again")
    fun failedFirstLoadRetries() {
        source.down = true
        refresher.refresh()
        assertNull(holder.current())

        source.down = false
        clock.advance(Duration.ofMinutes(1))
        refresher.refresh()

        assertEquals("c1", holder.current()?.policy?.commit)
    }

    @Test
    @DisplayName("before the refresh interval has passed, a tick asks nothing")
    fun notDueYet() {
        refresher.refresh()
        clock.advance(REFRESH_INTERVAL.minusSeconds(1))
        refresher.refresh()

        assertEquals(listOf("load"), source.calls)
    }

    @Test
    @DisplayName("an unchanged head reads no file")
    fun headUnchanged() {
        refresher.refresh()
        clock.advance(REFRESH_INTERVAL)
        refresher.refresh()

        assertEquals(listOf("load", "head"), source.calls)
    }

    @Test
    @DisplayName("a new head reloads the policy")
    fun headChanged() {
        refresher.refresh()
        source.head = "c2"
        clock.advance(REFRESH_INTERVAL)
        refresher.refresh()

        assertEquals("c2", holder.current()?.policy?.commit)
    }

    @Test
    @DisplayName("a failed reload keeps the policy loaded before")
    fun failedReloadKeepsPolicy() {
        refresher.refresh()
        source.head = "c2"
        source.down = true
        clock.advance(REFRESH_INTERVAL)
        refresher.refresh()

        assertEquals("c1", holder.current()?.policy?.commit)
    }

    /** Loads a one-line sample registry at its [head]; [down] makes every call fail. */
    class FakeLicensePolicySource(
        var head: String = "c1",
    ) : LicensePolicySource {
        var down = false
        val calls = mutableListOf<String>()

        override fun headCommit(): String = answer("head") { head }

        override fun load(): LicensePolicy = answer("load") { samplePolicy(head) }

        private fun <T> answer(
            call: String,
            value: () -> T,
        ): T {
            calls += call
            if (down) throw LicenseRegistryUnavailableException("Bitbucket is down")
            return value()
        }
    }

    class MutableClock(
        private var now: Instant = Instant.parse("2026-11-06T10:00:00Z"),
    ) : Clock() {
        fun advance(duration: Duration) {
            now = now.plus(duration)
        }

        override fun instant(): Instant = now

        override fun getZone(): ZoneOffset = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this
    }

    companion object {
        val REFRESH_INTERVAL: Duration = Duration.ofMinutes(15)

        fun samplePolicy(commit: String): LicensePolicy =
            LicenseRegistryParser.parse(mapOf("licenses-whitelist.txt" to "Apache-2.0|MIT"), commit)
    }
}
