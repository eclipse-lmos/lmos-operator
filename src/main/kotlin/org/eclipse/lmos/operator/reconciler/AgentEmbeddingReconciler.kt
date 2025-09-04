/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler

import io.javaoperatorsdk.operator.api.reconciler.Cleaner
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl
import io.javaoperatorsdk.operator.api.reconciler.Reconciler
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl
import io.javaoperatorsdk.operator.processing.retry.GradualRetry
import org.eclipse.lmos.classifier.core.Agent
import org.eclipse.lmos.classifier.core.Capability
import org.eclipse.lmos.classifier.core.SystemContext
import org.eclipse.lmos.classifier.core.semantic.EmbeddingHandler
import org.eclipse.lmos.operator.reconciler.generator.DEPLOYMENT_LABEL_KEY_SUBSET
import org.eclipse.lmos.operator.resources.AgentResource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

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
@ConditionalOnProperty(
    prefix = "lmos.router.embedding",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class AgentEmbeddingReconciler(
    private val embeddingHandler: EmbeddingHandler,
) : Reconciler<AgentResource>,
    Cleaner<AgentResource> {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    override fun reconcile(
        agentResource: AgentResource,
        context: Context<AgentResource>,
    ): UpdateControl<AgentResource> {
        log.info("Agent reconcile: Manage embeddings for agent '${agentResource.metadata.name}'")
        agentResource.convertToContextAgentMap().forEach { (context, agent) ->
            try {
                embeddingHandler.ingest(context, agent)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Failed to ingest embeddings for agent '${agent.id}' " +
                        "(tenant: '${context.tenantId}', channel: '${context.channelId}').",
                    e,
                )
            }
        }
        return UpdateControl.noUpdate()
    }

    override fun cleanup(
        agentResource: AgentResource?,
        context: Context<AgentResource>?,
    ): DeleteControl {
        log.info("Agent cleanup: Remove embeddings for agent '${agentResource?.metadata?.name}' due to agent reconcile.")
        agentResource?.convertToContextAgentMap()?.forEach { (context, agent) ->
            try {
                embeddingHandler.remove(context, agent)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Failed to remove embeddings for agent '${agent.id}' " +
                        "(tenant: '${context.tenantId}', channel: '${context.channelId}').",
                    e,
                )
            }
        }
        return DeleteControl.defaultDelete()
    }

    private fun AgentResource.convertToContextAgentMap(): Map<SystemContext, Agent> {
        val capabilities = createCapabilities()
        if (capabilities.isEmpty()) {
            log.warn("Skip embedding handling for agent '${metadata.name}', because the agent has no valid capabilities.")
            return emptyMap()
        }

        val systemContexts = createSystemContexts()
        if (systemContexts.isEmpty()) {
            log.warn("Skip embedding handling for agent '${metadata.name}', because the agent has no supported tenants or channels.")
            return emptyMap()
        }

        return systemContexts.associateWith {
            Agent(
                id = spec.id.ifBlank { metadata.name },
                name = metadata.name,
                address = "${metadata.name}.${metadata.namespace}.svc.cluster.local",
                capabilities = capabilities,
            )
        }
    }

    private fun AgentResource.createCapabilities(): List<Capability> =
        spec.providedCapabilities.mapNotNull { cap ->
            val examples = cap.examples.filter(String::isNotEmpty)
            if (cap.description.isNotEmpty() && examples.isNotEmpty()) {
                Capability(cap.id.ifBlank { cap.name }, cap.description, examples)
            } else {
                log.warn(
                    "Skip embedding handling for capability '${cap.name}' from agent '${metadata.name}', " +
                        "because the capability has no description or examples.",
                )
                null
            }
        }

    fun AgentResource.createSystemContexts(): List<SystemContext> {
        val subset = metadata.labels[DEPLOYMENT_LABEL_KEY_SUBSET] ?: "stable"
        return spec.supportedTenants.flatMap { tenant ->
            spec.supportedChannels.map { channel ->
                SystemContext(tenant, channel, subset)
            }
        }
    }
}
