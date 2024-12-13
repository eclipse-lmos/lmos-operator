/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator

import com.fasterxml.jackson.databind.ObjectMapper
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer
import io.fabric8.kubernetes.client.utils.KubernetesSerialization
import io.fabric8.kubernetes.client.utils.Serialization
import io.javaoperatorsdk.operator.springboot.starter.test.TestConfigurationProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.util.ResourceUtils
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.stream.Stream

@Configuration
open class TestConfig {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    @Bean
    @Primary
    open fun kubernetesClient(
        server: KubernetesMockServer,
        properties: TestConfigurationProperties,
        objectMapper: ObjectMapper,
    ): KubernetesClient {
        val client =
            server.createClient { it ->
                it.withKubernetesSerialization(
                    KubernetesSerialization(
                        objectMapper,
                        true,
                    ),
                )
            }
        Stream.concat(properties.crdPaths.stream(), properties.globalCrdPaths.stream()).forEach { crdPath: String? ->
            val crd: CustomResourceDefinition
            try {
                crd =
                    Serialization.unmarshal<Any>(FileInputStream(ResourceUtils.getFile(crdPath))) as CustomResourceDefinition
            } catch (e: FileNotFoundException) {
                log.warn("CRD with path {} not found!", crdPath)
                e.printStackTrace()
                return@forEach
            }
            client.apiextensions().v1().customResourceDefinitions().create(crd)
        }
        return client
    }
}
