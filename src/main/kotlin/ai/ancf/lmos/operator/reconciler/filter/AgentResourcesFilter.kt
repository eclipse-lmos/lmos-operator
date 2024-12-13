/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler.filter

import org.eclipse.lmos.operator.resources.AgentResource
import org.eclipse.lmos.operator.resources.ChannelResource
import java.util.function.Predicate

class AgentResourcesFilter(channelResource: ChannelResource) : Predicate<AgentResource> {
    private val labels: Map<String, String>

    init {
        val metadata = channelResource.metadata
        labels = metadata.labels
    }

    override fun test(agentResource: AgentResource): Boolean {
        val supportedTenants = agentResource.spec?.supportedTenants
        val tenantMatches =
            supportedTenants.isNullOrEmpty() ||
                supportedTenants.contains(labels["tenant"])

        val channelMatches = agentResource.spec?.supportedChannels?.contains(labels["channel"])

        return tenantMatches && channelMatches == true
    }
}
