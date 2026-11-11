package org.octopusden.octopus.license.inventory.impl.adapter

import org.octopusden.octopus.infrastructure.artifactory.client.ArtifactoryClassicClient
import org.octopusden.octopus.infrastructure.client.commons.ClientParametersProvider
import org.octopusden.octopus.infrastructure.client.commons.StandardBearerTokenCredentialProvider
import org.octopusden.octopus.infrastructure.xray.client.XrayClassicClient
import org.octopusden.octopus.license.inventory.impl.adapter.artifactory.ArtifactoryArtifactStore
import org.octopusden.octopus.license.inventory.impl.adapter.artifactory.ArtifactoryBuildRecords
import org.octopusden.octopus.license.inventory.impl.adapter.xray.XrayBinaryScanner
import org.octopusden.octopus.license.inventory.impl.adapter.xray.XrayLicenseDatabase
import org.octopusden.octopus.license.inventory.impl.port.ScanPorts
import java.time.Duration

/** The four ports over one JFrog platform; the clients are built once, the Xray wait budget per scan. */
internal class JfrogPorts(
    url: String,
    token: String,
    private val xrayWait: Duration,
    private val xrayPoll: Duration,
) {
    private val parameters =
        object : ClientParametersProvider {
            override fun getApiUrl() = url.trimEnd('/')

            override fun getAuth() = StandardBearerTokenCredentialProvider(token)
        }
    private val artifactory = ArtifactoryClassicClient(parameters)
    private val xray = XrayClassicClient(parameters)
    private val calls = JfrogCalls()

    fun create() =
        ScanPorts(
            records = ArtifactoryBuildRecords(artifactory, calls),
            store = ArtifactoryArtifactStore(artifactory, calls),
            scanner = XrayBinaryScanner(xray, calls, xrayWait, xrayPoll),
            database = XrayLicenseDatabase(xray, calls),
        )
}
