package org.octopusden.octopus.license.service.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

/*
 * The systems the service reads, under the keys every service shares in Spring Cloud Config: the
 * URLs are set once in its application.yml, the credentials come from Vault. Each value is required;
 * nullable only so its absence is reported by name at startup.
 */

@ConfigurationProperties("components-registry-service")
data class ComponentsRegistryServiceProperties(
    val url: String? = null,
)

/** Where the license registry is read from (D9). */
@ConfigurationProperties("bitbucket")
data class BitbucketProperties(
    val host: String? = null,
    val token: String? = null,
)

/** The JFrog platform: Artifactory, and Xray under the same URL. */
@ConfigurationProperties("artifactory")
data class ArtifactoryProperties(
    val url: String? = null,
    val token: String? = null,
)
