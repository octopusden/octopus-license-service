package org.octopusden.octopus.license.service.state.policy

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.policy.LicensePolicySource
import org.octopusden.octopus.license.policy.exception.InvalidLicenseRegistryException
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
    private val meterRegistry = SimpleMeterRegistry()
    private val refresher = LicensePolicyRefresher(source, holder, REFRESH_INTERVAL, clock, meterRegistry)

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

    @Test
    @DisplayName("every refresh that asks Bitbucket is counted by its outcome, so a failing refresh shows as a metric")
    fun refreshesCountedByOutcome() {
        refresher.refresh()
        clock.advance(REFRESH_INTERVAL)
        refresher.refresh()
        source.down = true
        clock.advance(REFRESH_INTERVAL)
        refresher.refresh()
        source.down = false
        source.invalid = true
        source.head = "c2"
        clock.advance(REFRESH_INTERVAL)
        refresher.refresh()

        assertEquals(
            mapOf("loaded" to 1.0, "unchanged" to 1.0, "unavailable" to 1.0, "invalid" to 1.0),
            listOf("loaded", "unchanged", "unavailable", "invalid").associateWith { refreshes(it) },
        )
    }

    @Test
    @DisplayName("a tick that is not due asks nothing and counts nothing")
    fun notDueIsNotCounted() {
        refresher.refresh()
        clock.advance(Duration.ofMinutes(1))
        refresher.refresh()

        assertEquals(1.0, meterRegistry.find(LicensePolicyRefresher.REFRESHES_METRIC).counters().sumOf { it.count() })
    }

    private fun refreshes(outcome: String): Double =
        meterRegistry
            .find(LicensePolicyRefresher.REFRESHES_METRIC)
            .tag("outcome", outcome)
            .counter()
            ?.count() ?: 0.0

    /** Loads a one-line sample registry at its [head]; [down] makes every call fail. */
    class FakeLicensePolicySource(
        var head: String = "c1",
    ) : LicensePolicySource {
        var down = false
        var invalid = false
        val calls = mutableListOf<String>()

        override fun headCommit(): String = answer("head") { head }

        override fun load(): LicensePolicy =
            answer("load") {
                if (invalid) throw InvalidLicenseRegistryException("licenses-whitelist.txt names no license")
                samplePolicy(head)
            }

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
            LicenseRegistryParser.parse(
                mapOf(
                    "licenses-whitelist.txt" to "Apache-2.0|MIT",
                    "licenses-hidden.txt" to "",
                    "merges.txt" to "",
                    "thirdparty-licenses.properties" to "",
                    "licenses.properties" to "",
                ),
                commit,
            )
    }
}
