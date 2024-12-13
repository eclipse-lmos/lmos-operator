/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler

import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.BooleanWithUndefined
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent
import org.eclipse.lmos.operator.reconciler.filter.AgentResourcesFilter
import org.eclipse.lmos.operator.reconciler.generator.RoutingChannelGenerator
import org.eclipse.lmos.operator.resolver.ResolverException
import org.eclipse.lmos.operator.resolver.impl.AgentResolveContext
import org.eclipse.lmos.operator.resolver.impl.AgentResolver
import org.eclipse.lmos.operator.resources.AgentResource
import org.eclipse.lmos.operator.resources.ChannelResource
import org.eclipse.lmos.operator.resources.ChannelRoutingResource
import org.eclipse.lmos.operator.resources.ChannelStatus
import org.eclipse.lmos.operator.resources.Labels
import org.eclipse.lmos.operator.resources.ResolveStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@KubernetesDependent(useSSA = BooleanWithUndefined.FALSE)
class ChannelDependentResource :
    CRUDKubernetesDependentResource<ChannelRoutingResource, ChannelResource>(ChannelRoutingResource::class.java) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    override fun desired(
        channelResource: ChannelResource,
        context: Context<ChannelResource>,
    ): ChannelRoutingResource {
        // Logic to define the desired state of the ChannelRoutingResource
        val namespace = channelResource.metadata.namespace
        log.info("Resolve required capabilities for channel ${channelResource.metadata.name} in namespace $namespace")

        val client = context.client

        // Retrieve all agents from the same namespace of the channel resource
        val subset = channelResource.metadata.labels[Labels.SUBSET] ?: "stable"
        val agentResources =
            client.resources(AgentResource::class.java).inNamespace(namespace)
                .withLabel(Labels.SUBSET, subset).list().items

        // Filter agents which support the tenant and channel of the channel resource
        val filteredAgentResources =
            agentResources.stream()
                .filter(AgentResourcesFilter(channelResource)).toList()

        val resolveContext = AgentResolveContext(filteredAgentResources)
        val requiredCapabilities = channelResource.spec.requiredCapabilities

        val resolver = AgentResolver()
        try {
            // Resolve required capabilities and wire them to the agents
            val wiredCapabilities = resolver.resolve(requiredCapabilities, resolveContext)
            val channelRoutingResource =
                RoutingChannelGenerator.createChannelRoutingResource(channelResource, wiredCapabilities, subset)
            channelResource.status = ChannelStatus(ResolveStatus.RESOLVED)
            log.info("Created ChannelRouting for channel ${channelResource.metadata.name} in namespace $namespace")
            return channelRoutingResource
        } catch (e: ResolverException) {
            // ChannelRoutingResource must be created because this method is not allowed to return null or throw an exception :(
            val channelRoutingResource =
                RoutingChannelGenerator.createChannelRoutingResource(channelResource, emptySet(), subset)
            log.error("Resolve failed for channel ${channelResource.metadata.name} in namespace $namespace", e)
            channelResource.status = ChannelStatus(ResolveStatus.UNRESOLVED, e.getUnresolvedRequiredCapabilities())
            return channelRoutingResource
        }
    }
}
