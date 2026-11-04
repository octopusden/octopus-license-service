package org.octopusden.octopus.license.client.impl

import feign.Feign
import feign.Request
import feign.codec.Decoder
import feign.jackson3.Jackson3Decoder
import feign.jackson3.Jackson3Encoder
import org.octopusden.octopus.license.client.LicenseServiceClient
import org.octopusden.octopus.license.client.LicenseServiceErrorDecoder
import org.octopusden.octopus.license.core.dto.LicenseReportRequest
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.util.concurrent.TimeUnit

class ClassicLicenseServiceClient(
    parametersProvider: LicenseServiceClientParametersProvider,
) : LicenseServiceClient {
    private val client = createClient(parametersProvider.getApiUrl())

    override fun startReport(request: LicenseReportRequest) = client.startReport(request)

    override fun getReport(id: String) = client.getReport(id)

    override fun downloadFiles(id: String) = client.downloadFiles(id)

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 30L
        const val READ_TIMEOUT_MINUTES = 5L

        val jsonMapper: JsonMapper = JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()

        fun createClient(apiUrl: String): LicenseServiceClient {
            val jsonDecoder = Jackson3Decoder(jsonMapper)
            val bytesDecoder = Decoder.Default()
            return Feign
                .builder()
                .options(
                    Request.Options(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS, READ_TIMEOUT_MINUTES, TimeUnit.MINUTES, true),
                ).encoder(Jackson3Encoder(jsonMapper))
                .decoder { response, type ->
                    if (type == ByteArray::class.java) bytesDecoder.decode(response, type) else jsonDecoder.decode(response, type)
                }.errorDecoder(LicenseServiceErrorDecoder(jsonMapper))
                .target(LicenseServiceClient::class.java, apiUrl)
        }
    }
}
