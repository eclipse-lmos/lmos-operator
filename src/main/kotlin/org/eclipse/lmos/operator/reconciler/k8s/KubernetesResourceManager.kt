/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler.k8s

import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.StatusDetails
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.client.KubernetesClient
import org.eclipse.lmos.operator.resources.AgentResource
import org.slf4j.LoggerFactory

@org.springframework.stereotype.Service
class KubernetesResourceManager(
    private val kubernetesClient: KubernetesClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createAgentResource(agentResource: AgentResource) {
        kubernetesClient.resources(AgentResource::class.java).createOrReplace(agentResource)
        log.info("Created Agent Resource: {}.", agentResource)
    }

    fun deleteAgentResource(deployment: Deployment) {
        val deleteStatus: List<StatusDetails> =
            kubernetesClient
                .resources(AgentResource::class.java)
                .inNamespace(deployment.metadata.namespace)
                .withName(deployment.metadata.name)
                .delete()
        log.info("Deleted Agent Resources for deployment '{}', status '{}'.", deployment.metadata.name, deleteStatus)
    }

    fun isDeploymentReady(deployment: Deployment): Boolean {
        val replicas = deployment.status.replicas
        val desiredReplicas = deployment.spec.replicas
        val availableReplicas = deployment.status.availableReplicas
        return (
            replicas != null &&
                availableReplicas != null &&
                replicas == desiredReplicas &&
                availableReplicas == desiredReplicas
        )
    }

    fun getServiceUrl(
        deployment: Deployment,
        path: String,
    ): String {
        val service = findService(deployment)
        val baseUrl = getBaseUrl(service)
        return if (path.startsWith("/")) {
            "$baseUrl$path"
        } else {
            "$baseUrl/$path"
        }.apply { log.info("Determined service URL: $this") }
    }

    private fun findService(deployment: Deployment): Service {
        val deploymentPodLabels =
            deployment.spec
                ?.template
                ?.metadata
                ?.labels ?: emptyMap()

        val servicesInNamespace =
            kubernetesClient
                .services()
                .inNamespace(deployment.metadata.namespace)
                .list()

        val matchingServices =
            servicesInNamespace.items.filter { service ->
                val selectors = service.spec?.selector
                if (selectors.isNullOrEmpty()) return@filter false
                selectors.all { (key, value) -> deploymentPodLabels[key] == value }
            }

        if (matchingServices.size != 1) {
            val name = deployment.metadata.name
            log.error(
                "Expected exactly one service for deployment $name, but got ${matchingServices.size}; $matchingServices",
            )
            throw IllegalStateException("Expected exactly one service for deployment $name, but got ${matchingServices.size}")
        }

        return matchingServices.first()
    }

    private fun getBaseUrl(service: Service): String {
        val ports = service.spec.ports

        val appProtocolCandidates =
            ports.filter {
                it.appProtocol?.equals("http", ignoreCase = true) == true ||
                    it.appProtocol?.equals("https", ignoreCase = true) == true
            }
        val nameCandidates =
            ports.filter {
                it.name?.equals("http", ignoreCase = true) == true ||
                    it.name?.equals("https", ignoreCase = true) == true
            }

        val primaryPort =
            if (appProtocolCandidates.size == 1) {
                appProtocolCandidates.first()
            } else if (nameCandidates.size == 1) {
                nameCandidates.first()
            } else {
                ports.first()
            }

        val isHttps =
            primaryPort.appProtocol?.equals("https", ignoreCase = true) == true ||
                primaryPort.name?.equals("https", ignoreCase = true) == true ||
                primaryPort.port == 443
        val protocol = if (isHttps) "https" else "http"

        return "$protocol://${service.metadata.name}.${service.metadata.namespace}.svc.cluster.local:${primaryPort.port}"
    }
}
