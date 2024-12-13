/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler

import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration
import io.javaoperatorsdk.operator.api.reconciler.Reconciler
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent
import org.eclipse.lmos.operator.resources.ChannelResource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
@ControllerConfiguration(dependents = [Dependent(type = ChannelDependentResource::class)])
class ChannelReconciler : Reconciler<ChannelResource> {
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
}
