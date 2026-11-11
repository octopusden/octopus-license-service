package org.octopusden.octopus.license.inventory.impl.adapter

import org.octopusden.octopus.infrastructure.xray.client.XrayClient
import org.octopusden.octopus.infrastructure.xray.client.dto.ArtifactStatusRequest
import org.octopusden.octopus.infrastructure.xray.client.dto.ArtifactSummaryRequest
import org.octopusden.octopus.infrastructure.xray.client.dto.ScanState
import org.octopusden.octopus.infrastructure.xray.client.exception.XrayClientException
import org.octopusden.octopus.license.inventory.exception.InventorySourceUnavailableException
import org.octopusden.octopus.license.inventory.impl.port.BinaryScanner
import org.octopusden.octopus.license.inventory.impl.port.StoredFile
import org.octopusden.octopus.license.inventory.model.PackageId
import java.time.Duration

/**
 * Xray's scan of a stored file (D12). It first waits while Xray is still scanning the file, from one
 * budget per scan, then reads the summary whatever the status said, exactly as the prototype reads
 * it: a summary that answers an error for the path means "not indexed". A package Xray lists under
 * no license is not in the summary at all (D10.10). One instance per scan: the budget is the scan's.
 */
internal class XrayBinaryScanner(
    private val client: XrayClient,
    private val calls: JfrogCalls,
    waitBudget: Duration,
    private val pollInterval: Duration,
    private val sleep: (Duration) -> Unit = { Thread.sleep(it) },
) : BinaryScanner {
    private var budgetLeft = waitBudget

    override fun scan(file: StoredFile): Map<PackageId, Set<String>>? {
        waitWhilePending(file)
        val summary =
            calls.call(JfrogCalls.XRAY, "summary of ${file.fullPath}") {
                client.getArtifactSummary(ArtifactSummaryRequest(listOf("$DEFAULT_WATCH/${file.fullPath}")))
            }
        if (summary.errors.isNotEmpty()) return null
        val found = linkedMapOf<PackageId, MutableSet<String>>()
        summary.artifacts.forEach { artifact ->
            artifact.licenses.forEach { license ->
                license.components.forEach { found.getOrPut(XrayComponentIds.toPackageId(it), ::linkedSetOf) += license.name }
            }
        }
        return found
    }

    private fun waitWhilePending(file: StoredFile) {
        while (status(file) in PENDING && budgetLeft > Duration.ZERO) {
            val pause = minOf(pollInterval, budgetLeft)
            sleep(pause)
            budgetLeft -= pause
        }
    }

    // A file Xray will not or cannot report on answers a client error here; the summary decides then.
    @Suppress("SwallowedException")
    private fun status(file: StoredFile): ScanState? =
        try {
            calls
                .call(JfrogCalls.XRAY, "scan status of ${file.fullPath}") {
                    client.getArtifactStatus(ArtifactStatusRequest(file.repo, "${file.path}/${file.name}"))
                }.let { it.details?.sca?.status ?: it.overall?.status }
        } catch (e: InventorySourceUnavailableException) {
            if ((e.cause as? XrayClientException)?.status in CLIENT_ERRORS) null else throw e
        }

    private companion object {
        const val DEFAULT_WATCH = "default"
        val PENDING = setOf(ScanState.PENDING, ScanState.SCANNING)
        val CLIENT_ERRORS = 400..499
    }
}
