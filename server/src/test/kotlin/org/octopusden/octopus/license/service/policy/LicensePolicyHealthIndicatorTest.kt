package org.octopusden.octopus.license.service.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.octopusden.octopus.license.service.policy.LicensePolicyRefresherTest.Companion.REFRESH_INTERVAL
import org.octopusden.octopus.license.service.policy.LicensePolicyRefresherTest.FakeLicensePolicySource
import org.octopusden.octopus.license.service.policy.LicensePolicyRefresherTest.MutableClock
import org.springframework.boot.health.contributor.Status

/** Readiness follows the policy in memory, never Bitbucket itself (D7). */
class LicensePolicyHealthIndicatorTest {
    private val source = FakeLicensePolicySource()
    private val clock = MutableClock()
    private val holder = LicensePolicyHolder()
    private val refresher = LicensePolicyRefresher(source, holder, REFRESH_INTERVAL, clock)
    private val indicator = LicensePolicyHealthIndicator(holder)

    @Test
    @DisplayName("D7: Bitbucket going down after the first load keeps the service ready, on the policy it has")
    fun bitbucketDownAfterFirstLoad() {
        refresher.refresh()
        source.head = "c2"
        source.down = true
        clock.advance(REFRESH_INTERVAL)
        refresher.refresh()

        val health = indicator.health()
        assertEquals(Status.UP, health.status)
        assertEquals("c1", health.details["commit"])
    }

    @Test
    @DisplayName("D7: Bitbucket down from the start keeps the service unready, with nothing to judge with")
    fun bitbucketDownFromTheStart() {
        source.down = true
        refresher.refresh()

        assertEquals(Status.DOWN, indicator.health().status)
    }
}
