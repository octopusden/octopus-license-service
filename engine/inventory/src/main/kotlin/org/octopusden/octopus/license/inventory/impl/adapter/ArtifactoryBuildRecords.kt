package org.octopusden.octopus.license.inventory.impl.adapter

import com.fasterxml.jackson.databind.ObjectMapper
import org.octopusden.octopus.infrastructure.artifactory.client.ArtifactoryClient
import org.octopusden.octopus.infrastructure.artifactory.client.dto.BuildInfo
import org.octopusden.octopus.license.inventory.impl.port.BuildDependency
import org.octopusden.octopus.license.inventory.impl.port.BuildModule
import org.octopusden.octopus.license.inventory.impl.port.BuildRecord
import org.octopusden.octopus.license.inventory.impl.port.BuildRecords
import org.octopusden.octopus.license.inventory.impl.port.BuiltFile
import org.octopusden.octopus.license.inventory.model.BuildRef

/** Build-info from Artifactory's build-info repository, in this module's terms. */
internal class ArtifactoryBuildRecords(
    private val client: ArtifactoryClient,
    private val calls: JfrogCalls,
) : BuildRecords {
    // Missing and unreadable build-info both mean "no build", as the prototype reads them (D4).
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun find(
        name: String,
        number: String,
    ): BuildRecord? =
        try {
            calls.call(JfrogCalls.ARTIFACTORY, "build $name/$number") { client.getBuildInfo(name, number, BUILD_REPO) }.buildInfo.toRecord()
        } catch (_: RuntimeException) {
            null
        }

    override fun findByPublishedSha1(sha1: String): List<BuildRef> =
        calls
            .call(JfrogCalls.ARTIFACTORY, "builds of $sha1") {
                client.searchBuildsByAQL("""builds.find({"module.artifact.sha1":${json(sha1)}}).include("name","number","repo")""")
            }.results
            .mapNotNull { build -> if (build.name != null && build.number != null) BuildRef(build.name!!, build.number!!) else null }

    private fun BuildInfo.toRecord() =
        BuildRecord(
            ref = BuildRef(name, number),
            agent = agent?.name ?: buildAgent?.name,
            modules =
                modules.orEmpty().map { module ->
                    BuildModule(
                        id = module.id,
                        type = module.type,
                        files = module.artifacts.orEmpty().map { BuiltFile(it.name, it.sha1, it.path) },
                        dependencies = module.dependencies.orEmpty().map { BuildDependency(it.id, it.type, it.sha1, it.scopes.orEmpty()) },
                    )
                },
        )

    private companion object {
        const val BUILD_REPO = "artifactory-build-info"
        private val mapper = ObjectMapper()

        fun json(value: String): String = mapper.writeValueAsString(value)
    }
}
