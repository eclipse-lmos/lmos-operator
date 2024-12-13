/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator

import com.fasterxml.jackson.databind.ObjectMapper
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.utils.KubernetesSerialization
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class OperatorConfig {
    @Bean
    open fun kubernetesClient(objectMapper: ObjectMapper): KubernetesClient {
        return KubernetesClientBuilder()
            .withKubernetesSerialization(KubernetesSerialization(objectMapper, true))
            .build()
    }
}
