package org.octopusden.octopus.license.service

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class LicenseServiceApplication

fun main(args: Array<String>) {
    runApplication<LicenseServiceApplication>(*args)
}
