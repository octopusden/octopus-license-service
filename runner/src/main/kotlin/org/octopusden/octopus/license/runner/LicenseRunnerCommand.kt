package org.octopusden.octopus.license.runner

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.octopusden.octopus.license.client.impl.ClassicLicenseServiceClient
import org.octopusden.octopus.license.client.impl.LicenseServiceClientParametersProvider
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration

/**
 * Generates the license report of one component version through the license service and writes
 * the report and its license files to [outputDir]. Always exits 0: license problems and service
 * problems are only reported for now.
 */
class LicenseRunnerCommand : CliktCommand(name = "license-runner") {
    private val url by option("--url", envvar = "LICENSE_SERVICE_URL", help = "License service base URL").required()
    private val component by option("--component", help = "Component name in the components registry").required()
    private val version by option("--version", help = "Component version").required()
    private val outputDir by option("--output-dir", help = "Where the report and the license files go")
        .path()
        .default(Path.of("license-report"))
    private val pollInterval by option("--poll-interval", help = "ISO-8601 duration between status checks")
        .convert { Duration.parse(it) }
        .default(Duration.ofSeconds(DEFAULT_POLL_INTERVAL_SECONDS))
    private val timeout by option("--timeout", help = "ISO-8601 duration to wait for the report")
        .convert { Duration.parse(it) }
        .default(Duration.ofMinutes(DEFAULT_TIMEOUT_MINUTES))

    override fun help(context: Context) = "Generate the third-party license files of a component version."

    override fun run() {
        val client = ClassicLicenseServiceClient(
            object : LicenseServiceClientParametersProvider {
                override fun getApiUrl() = url
            },
        )
        val outcome = LicenseReportRunner(client, pollInterval, timeout, Clock.systemUTC()) { Thread.sleep(it) }
            .run(component, version)
        write(outcome)
        val underTeamCity = System.getenv(TEAMCITY_VERSION_ENV) != null
        BuildLog.lines(component, version, outcome).forEach {
            echo(if (it.warning && underTeamCity) BuildLog.teamCityWarning(it.text) else it.text)
        }
    }

    private fun write(outcome: RunOutcome) {
        if (outcome !is RunOutcome.Finished) return
        Files.createDirectories(outputDir)
        jsonMapper.writeValue(outputDir.resolve(REPORT_FILE).toFile(), outcome.report)
        outcome.files?.let { Files.write(outputDir.resolve("$component-$version-licenses.zip"), it) }
    }

    private companion object {
        const val DEFAULT_POLL_INTERVAL_SECONDS = 5L
        const val DEFAULT_TIMEOUT_MINUTES = 30L
        const val REPORT_FILE = "license-report.json"
        const val TEAMCITY_VERSION_ENV = "TEAMCITY_VERSION"

        val jsonMapper: JsonMapper = JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build()
    }
}

fun main(args: Array<String>) = LicenseRunnerCommand().main(args)
