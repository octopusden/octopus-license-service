package org.octopusden.octopus.license.service.service.impl

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.octopusden.octopus.license.core.dto.GenerationInputs
import org.octopusden.octopus.license.core.dto.LicenseReport
import org.octopusden.octopus.license.core.dto.LicenseReportMode
import org.octopusden.octopus.license.core.dto.LicenseReportRequest
import org.octopusden.octopus.license.core.dto.LicenseReportSummary
import org.octopusden.octopus.license.core.exception.ServiceBusyException
import org.octopusden.octopus.license.service.service.GeneratedReport
import org.octopusden.octopus.license.service.service.LicenseReportGenerator
import org.octopusden.octopus.license.service.state.report.LicenseReportStore
import org.octopusden.octopus.license.target.LicenseTargetResolver
import org.octopusden.octopus.license.target.model.ComponentVersion
import org.octopusden.octopus.license.target.model.DistributionSpec
import org.octopusden.octopus.license.target.model.LicenseTarget
import org.octopusden.octopus.license.target.model.TargetResolution
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.TaskRejectedException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class LicenseReportServiceImplTest {
    private val clock = Clock.systemUTC()
    private val store = LicenseReportStore(clock, Duration.ofHours(1), Long.MAX_VALUE)
    private val generator = object : LicenseReportGenerator {
        override fun generate(
            target: LicenseTarget,
            mode: LicenseReportMode,
        ) = GeneratedReport(
            LicenseReportSummary(GenerationInputs(null, Instant.EPOCH, "test"), null, emptyMap(), emptyList()),
            byteArrayOf(),
        )
    }

    private val resolver = object : LicenseTargetResolver {
        override fun resolve(
            component: String,
            version: String,
        ) = TargetResolution.Resolved(
            LicenseTarget(
                componentVersion = ComponentVersion(component, version),
                artifactVersions = listOf(version),
                buildSystem = "GRADLE",
                labels = emptySet(),
                distribution = DistributionSpec(emptyList(), emptyList()),
            ),
        )
    }

    @Test
    @DisplayName("a start that joins a report never gets one whose queuing is refused: no 202 for work that was not queued")
    fun joinNeverSeesRefusedReport() {
        val concurrentStart = AtomicReference<Result<LicenseReport>>()
        val firstSubmission = AtomicBoolean(true)
        lateinit var service: LicenseReportServiceImpl
        // The queue is full. While the first start submits its task, a second start for the same
        // component version arrives on another thread.
        val fullExecutor = TaskExecutor {
            if (firstSubmission.getAndSet(false)) {
                thread { concurrentStart.set(runCatching { service.start(REQUEST) }) }.join(CONCURRENT_START_WAIT_MS)
            }
            throw TaskRejectedException("queue full")
        }
        service = LicenseReportServiceImpl(store, generator, resolver, fullExecutor, clock, SimpleMeterRegistry())

        assertThrows<ServiceBusyException> { service.start(REQUEST) }

        awaitResult(concurrentStart).onSuccess { joined ->
            assertNotNull(store.find(joined.id), "the concurrent start got report ${joined.id}, which no longer exists")
        }
    }

    private fun awaitResult(result: AtomicReference<Result<LicenseReport>>): Result<LicenseReport> {
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (result.get() == null && System.nanoTime() < deadline) {
            Thread.onSpinWait()
        }
        return requireNotNull(result.get()) { "the concurrent start did not finish" }
    }

    private companion object {
        val REQUEST = LicenseReportRequest("some-component", "1.2.3")
        const val CONCURRENT_START_WAIT_MS = 200L
    }
}
