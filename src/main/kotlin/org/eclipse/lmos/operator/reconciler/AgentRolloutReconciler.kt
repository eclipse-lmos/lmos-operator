/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler

import io.javaoperatorsdk.operator.api.config.informer.Informer
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
import org.eclipse.lmos.operator.resources.RolloutResource
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

private const val ROLLOUT_NOT_READY_RECONCILE_INTERVAL_SECONDS = 10L

@Component
@ControllerConfiguration
@Informer(labelSelector = LABEL_SELECTOR)
@GradualRetry(
    initialInterval = ERROR_RETRY_INITIAL_INTERVAL_MS,
    intervalMultiplier = ERROR_RETRY_INTERVAL_MULTIPLIER,
    maxAttempts = ERROR_RETRY_MAX_ATTEMPTS,
)
@ConditionalOnProperty(
    prefix = "lmos.operator.rollout",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class AgentRolloutReconciler(
    private val kubernetesResourceManager: KubernetesResourceManager,
    private val agentClient: AgentClient,
) : Reconciler<RolloutResource>,
    Cleaner<RolloutResource> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun reconcile(
        rollout: RolloutResource,
        context: Context<RolloutResource>,
    ): UpdateControl<RolloutResource> {
        val rolloutReady = kubernetesResourceManager.isRolloutReady(rollout)
        if (rolloutReady) {
            log.info("Rollout reconcile: Create agent resource for rollout '{}'.", rollout.metadata.name)
            try {
                val agentSpecUrl = kubernetesResourceManager.getServiceUrl(rollout, WELL_KNOWN_AGENT_SPEC_ENDPOINT)
                val agentSpec = agentClient.get(agentSpecUrl, AgentSpecification::class.java)
                val agentResource = AgentGenerator.createAgentResource(rollout, agentSpec)
                kubernetesResourceManager.createAgentResource(agentResource)
                log.info("Creating agent resource '{}' in namespace '{}'.", agentResource.metadata.name, agentResource.metadata.namespace)
                return UpdateControl.noUpdate()
            } catch (e: Exception) {
                throw IllegalStateException("Failed to create agent resource for rollout '${rollout.metadata.name}'.", e)
            }
        }
        return UpdateControl.noUpdate<RolloutResource>().rescheduleAfter(ROLLOUT_NOT_READY_RECONCILE_INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    override fun cleanup(
        rollout: RolloutResource,
        context: Context<RolloutResource?>?,
    ): DeleteControl {
        log.info("Rollout cleanup: Delete agent resource '{}' due to reconcile.", rollout.metadata.name)
        kubernetesResourceManager.deleteAgentResource(rollout)
        return DeleteControl.defaultDelete()
    }
}
