/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.utils.KubernetesSerialization
import io.fabric8.kubernetes.client.utils.Serialization
import io.javaoperatorsdk.operator.springboot.starter.OperatorAutoConfiguration
import io.javaoperatorsdk.operator.springboot.starter.test.TestConfigurationProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.util.ResourceUtils
import org.testcontainers.k3s.K3sContainer
import org.testcontainers.utility.DockerImageName
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.stream.Stream


@TestConfiguration
@ImportAutoConfiguration(OperatorAutoConfiguration::class)
@EnableConfigurationProperties(
    TestConfigurationProperties::class
)
class TestConfig {

    @Bean
    fun k3sKubernetesContainer(): K3sContainer {
        val k3sContainer = K3sContainer(DockerImageName.parse("rancher/k3s:v1.21.3-k3s1"))
        k3sContainer.start()
        return k3sContainer
    }

    @Bean
    fun kubernetesClient(
        k3sKubernetesContainer: K3sContainer,
        properties: TestConfigurationProperties,
    ): KubernetesClient {
        val config = Config.fromKubeconfig(k3sKubernetesContainer.kubeConfigYaml)
        config.namespace = "default"

        val client = KubernetesClientBuilder()
            .withConfig(config)
            .withKubernetesSerialization(
                KubernetesSerialization(
                    Serialization.jsonMapper().registerKotlinModule(), true
                )
            )
            .build()

        Stream.concat(properties.crdPaths.stream(), properties.globalCrdPaths.stream())
            .forEach { crdPath: String? ->
                val crd: CustomResourceDefinition
                try {
                    crd = Serialization.unmarshal(FileInputStream(ResourceUtils.getFile(crdPath)))
                } catch (e: FileNotFoundException) {
                    log.warn("CRD with path {} not found!", crdPath)
                    e.printStackTrace()
                    return@forEach
                }
                client.apiextensions().v1().customResourceDefinitions().create(crd)
            }

        return client
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(TestConfiguration::class.java)
    }

}
