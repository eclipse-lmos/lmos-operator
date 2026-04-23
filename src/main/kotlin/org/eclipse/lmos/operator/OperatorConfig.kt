/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.http.HttpClient
import io.fabric8.kubernetes.client.utils.KubernetesSerialization
import io.fabric8.openshift.client.OpenShiftClient
import io.javaoperatorsdk.operator.springboot.starter.OperatorConfigurationProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import java.util.Optional

@Configuration
open class OperatorConfig {
    @Autowired
    private lateinit var configuration: OperatorConfigurationProperties

    @Bean
    @ConditionalOnMissingBean
    open fun webClientBuilder(): WebClient.Builder = WebClient.builder()

    @Bean
    @ConditionalOnMissingBean
    open fun objectMapper(): ObjectMapper = ObjectMapper().registerKotlinModule()

    @Bean
    @ConditionalOnMissingBean
    open fun kubernetesClient(
        httpClientFactory: Optional<HttpClient.Factory?>,
        config: Config?,
        objectMapper: ObjectMapper,
    ): KubernetesClient {
        objectMapper.registerKotlinModule()
        return if (configuration.getClient().isOpenshift()) {
            httpClientFactory
                .map<OpenShiftClient> { it: HttpClient.Factory? ->
                    KubernetesClientBuilder()
                        .withHttpClientFactory(it)
                        .withConfig(config)
                        .withKubernetesSerialization(KubernetesSerialization(objectMapper, true))
                        .build()
                        .adapt<OpenShiftClient>(OpenShiftClient::class.java)
                } // new DefaultOpenShiftClient(it.createHttpClient(config),
                // new OpenShiftConfig(config)))
                .orElseGet {
                    KubernetesClientBuilder()
                        .withConfig(config)
                        .withKubernetesSerialization(KubernetesSerialization(objectMapper, true))
                        .build()
                        .adapt<OpenShiftClient>(OpenShiftClient::class.java)
                }
        } else {
            httpClientFactory
                .map<KubernetesClient> { it: HttpClient.Factory? ->
                    KubernetesClientBuilder()
                        .withHttpClientFactory(it)
                        .withConfig(config)
                        .withKubernetesSerialization(KubernetesSerialization(objectMapper, true))
                        .build()
                }.orElseGet {
                    KubernetesClientBuilder()
                        .withConfig(config)
                        .withKubernetesSerialization(KubernetesSerialization(objectMapper, true))
                        .build()
                }
        }
    }
}
