/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler

import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration
import io.javaoperatorsdk.operator.api.reconciler.Reconciler
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent
import io.javaoperatorsdk.operator.processing.event.ResourceID
import io.javaoperatorsdk.operator.processing.event.source.EventSource
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource
import org.eclipse.lmos.operator.resources.AgentResource
import org.eclipse.lmos.operator.resources.ChannelResource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
@ControllerConfiguration(dependents = [Dependent(type = ChannelDependentResource::class)])
class ChannelReconciler : Reconciler<ChannelResource>, EventSourceInitializer<ChannelResource> {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    override fun reconcile(
        channelResource: ChannelResource,
        context: Context<ChannelResource>,
    ): UpdateControl<ChannelResource> {
        log.debug("Channel reconcile: ${channelResource.metadata.namespace}/${channelResource.metadata.name}")
        /*
                   The dependent ChannelRoutingDependentResource is automatically reconciled before the ChannelReconciler is executed.
                   Optional<ChannelRoutingResource> secondaryResource = context.getSecondaryResource(ChannelRoutingResource.class);
                   Unfortunately I don't know yet how the status of the subresource can be updated.
         */

        return UpdateControl.patchStatus(channelResource)
    }

    override fun prepareEventSources(context: EventSourceContext<ChannelResource>): Map<String, EventSource> {
        val config = InformerConfiguration
            .from(AgentResource::class.java)
            .withSecondaryToPrimaryMapper { agent ->
                context.client.resources(ChannelResource::class.java)
                    // At this point, AgentResourcesFilter could be used to find the matching Channels for the given Agent.
                    // However, this filter cannot be used due to an edge case: if a supportedChannel or supportedTenant is
                    // removed from the Agent, the filter will no longer match, and the affected Channels won't be reconciled.
                    // As a result, such Channels may remain in an incorrect RESOLVED state. To address this, all Channels
                    // in the namespace are considered to ensure proper reconciliation.
                    // This edge case could only be fixed within the JOSDK. The InformerEventSource would need to provide
                    // the old Agent object to the withSecondaryToPrimaryMapper method, enabling proper handling of such cases.
                    .inNamespace(agent.metadata.namespace).list().items
                    .map { ResourceID(it.metadata.name, it.metadata.namespace) }
                    .toSet()
            }
            .build()
        return EventSourceInitializer.nameEventSources(InformerEventSource(config, context))
    }

}
