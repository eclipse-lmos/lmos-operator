/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler

import io.fabric8.kubernetes.api.model.apps.Deployment
import io.javaoperatorsdk.operator.api.reconciler.Cleaner
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl
import io.javaoperatorsdk.operator.api.reconciler.Reconciler
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl
import io.javaoperatorsdk.operator.processing.retry.GradualRetry
import org.eclipse.lmos.operator.reconciler.client.AgentClient
import org.eclipse.lmos.operator.reconciler.generator.AgentGenerator
import org.eclipse.lmos.operator.reconciler.k8s.KubernetesResourceManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

private const val WELL_KNOWN_AGENT_SPEC_ENDPOINT = ".well-known/capabilities.json"
private const val DEPLOYMENT_NOT_READY_RECONCILE_INTERVAL_SECONDS = 10L
private const val ERROR_RETRY_INITIAL_INTERVAL_MS = 5000L
private const val ERROR_RETRY_INTERVAL_MULTIPLIER = 1.5
private const val ERROR_RETRY_MAX_ATTEMPTS = 3

@Component
@ControllerConfiguration(labelSelector = "lmos-agent=true")
@GradualRetry(
    initialInterval = ERROR_RETRY_INITIAL_INTERVAL_MS,
    intervalMultiplier = ERROR_RETRY_INTERVAL_MULTIPLIER,
    maxAttempts = ERROR_RETRY_MAX_ATTEMPTS,
)
class AgentDeploymentReconciler(
    private val kubernetesResourceManager: KubernetesResourceManager,
    private val agentClient: AgentClient,
) : Reconciler<Deployment>,
    Cleaner<Deployment> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun reconcile(
        deployment: Deployment,
        context: Context<Deployment>,
    ): UpdateControl<Deployment> {
        val deploymentReady = kubernetesResourceManager.isDeploymentReady(deployment)
        if (deploymentReady) {
            log.info("Deployment reconcile: Create agent resource for deployment '{}'.", deployment.metadata.name)
            try {
                val agentSpecUrl = kubernetesResourceManager.getServiceUrl(deployment, WELL_KNOWN_AGENT_SPEC_ENDPOINT)
                val agentSpec = agentClient.get(agentSpecUrl, AgentSpecification::class.java)
                val agentResource = AgentGenerator.createAgentResource(deployment, agentSpec)
                kubernetesResourceManager.createAgentResource(agentResource)
                log.info("Creating agent resource '{}' in namespace '{}'.", agentResource.metadata.name, agentResource.metadata.namespace)
                return UpdateControl.noUpdate()
            } catch (e: Exception) {
                throw IllegalStateException("Failed to create agent resource for deployment '${deployment.metadata.name}'.", e)
            }
        }
        return UpdateControl.noUpdate<Deployment>().rescheduleAfter(DEPLOYMENT_NOT_READY_RECONCILE_INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    override fun cleanup(
        deployment: Deployment,
        context: Context<Deployment?>?,
    ): DeleteControl {
        log.info("Deployment cleanup: Delete agent resource '{}' due to reconcile.", deployment.metadata.name)
        kubernetesResourceManager.deleteAgentResource(deployment)
        return DeleteControl.defaultDelete()
    }
}

data class AgentSpecification(
    val id: String,
    val description: String,
    val supportedTenants: Set<String>,
    val supportedChannels: Set<String>,
    val capabilities: List<CapabilitySpecification>,
)

data class CapabilitySpecification(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val examples: List<String>,
)
