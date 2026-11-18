package org.octopusden.octopus.license.runner

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.parameters.options.OptionCallTransformContext
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.octopusden.octopus.license.client.impl.ClassicLicenseServiceClient
import org.octopusden.octopus.license.client.impl.LicenseServiceClientParametersProvider
import org.octopusden.octopus.license.core.dto.LicenseReportMode
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
        .default(Path.of(DEFAULT_OUTPUT_DIR))
    private val checkOnly by option(
        "--check-only",
        help = "Quick check on what Xray knows now, for regular builds: no wait for Xray, no license files",
    ).flag()
    private val pollInterval by option("--poll-interval", help = "Time between status checks, such as 5s or PT5S")
        .convert { duration(it) }
        .default(Duration.ofSeconds(DEFAULT_POLL_INTERVAL_SECONDS))
    private val timeout by option(
        "--timeout",
        help = "How long to wait for the report, such as 45m or PT45M; a call still under way then is abandoned",
    ).convert { duration(it) }
        .default(Duration.ofMinutes(DEFAULT_TIMEOUT_MINUTES))

    private fun OptionCallTransformContext.duration(text: String) =
        Durations.parse(text) ?: fail("'$text' is not a duration; use 45m, 30s, 2h or ISO-8601 such as PT45M")

    override fun help(context: Context) = "Generate the third-party license files of a component version."

    override fun run() {
        runCatching { report() }.onFailure {
            echo(
                BuildLog.render(
                    LogLine("License runner failed for $component:$version: ${it.message ?: it.javaClass.name}", warning = true),
                ),
            )
        }
    }

    private fun report() {
        removePreviousOutput()
        val client = ClassicLicenseServiceClient(
            object : LicenseServiceClientParametersProvider {
                override fun getApiUrl() = url
            },
        )
        val mode = if (checkOnly) LicenseReportMode.CHECK else LicenseReportMode.FULL
        val outcome = LicenseReportRunner(client, pollInterval, timeout, Clock.systemUTC()) { Thread.sleep(it) }
            .run(component, version, mode)
        BuildLog.lines(component, version, outcome).forEach { echo(BuildLog.render(it)) }
        write(outcome)
    }

    private fun removePreviousOutput() = removePreviousOutput(outputDir)

    private fun write(outcome: RunOutcome) {
        if (outcome !is RunOutcome.Finished) return
        Files.createDirectories(outputDir)
        jsonMapper.writeValue(outputDir.resolve(REPORT_FILE).toFile(), outcome.report)
        outcome.files?.let { Files.write(outputDir.resolve("$component-$version$ZIP_SUFFIX"), it) }
    }

    companion object {
        const val DEFAULT_OUTPUT_DIR = "license-report"
        private const val OUTPUT_DIR_OPTION = "--output-dir"
        private const val END_OF_OPTIONS = "--"
        private val VALUE_OPTIONS =
            setOf("--url", "--component", "--version", OUTPUT_DIR_OPTION, "--poll-interval", "--timeout")
        private const val DEFAULT_POLL_INTERVAL_SECONDS = 5L
        private const val DEFAULT_TIMEOUT_MINUTES = 30L
        private const val REPORT_FILE = "license-report.json"
        private const val ZIP_SUFFIX = "-licenses.zip"

        private val jsonMapper: JsonMapper = JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build()

        /** A reused output directory must not publish an earlier run's report as this run's result. */
        fun removePreviousOutput(outputDir: Path) {
            if (!Files.isDirectory(outputDir)) return
            Files.deleteIfExists(outputDir.resolve(REPORT_FILE))
            Files.newDirectoryStream(outputDir, "*$ZIP_SUFFIX").use { zips -> zips.forEach(Files::delete) }
        }

        /**
         * The output directory named in [args], read without Clikt: for when Clikt could not parse
         * them. Read the way Clikt reads them: the last `--output-dir` wins, the token after an option
         * that takes a value is that value even when it starts with `--`, and nothing after `--` is
         * an option.
         */
        fun outputDirIn(args: Array<String>): Path {
            var outputDir: String? = null
            var i = 0
            while (i < args.size && args[i] != END_OF_OPTIONS) {
                val token = args[i]
                when {
                    token == OUTPUT_DIR_OPTION -> outputDir = args.getOrNull(i + 1)
                    token.startsWith("$OUTPUT_DIR_OPTION=") -> outputDir = token.substringAfter("=")
                }
                i += if (token in VALUE_OPTIONS) 2 else 1
            }
            return Path.of(outputDir ?: DEFAULT_OUTPUT_DIR)
        }
    }
}

/**
 * Like Clikt's own `main`, but arguments it cannot read end in a warning and exit 0 too: a typo in
 * the build settings must not fail the build any more than an unavailable service does.
 */
fun main(args: Array<String>) {
    val command = LicenseRunnerCommand()
    try {
        command.parse(args)
    } catch (e: CliktError) {
        command.echoFormattedHelp(e)
        if (e.statusCode != 0) {
            runCatching { LicenseRunnerCommand.removePreviousOutput(LicenseRunnerCommand.outputDirIn(args)) }
            command.echo(BuildLog.render(LogLine("License runner did not run: ${e.message ?: "invalid arguments"}", warning = true)))
        }
    }
}
