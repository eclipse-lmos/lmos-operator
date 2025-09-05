/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler.filter

import org.eclipse.lmos.operator.DEPLOYMENT_SUBSET_LABEL_KEY
import org.eclipse.lmos.operator.DEPLOYMENT_SUBSET_LABEL_DEFAULT_VALUE
import org.eclipse.lmos.operator.resources.AgentResource
import org.eclipse.lmos.operator.resources.ChannelResource
import java.util.function.Predicate

class AgentResourcesFilter(
    channelResource: ChannelResource,
) : Predicate<AgentResource> {
    private val labels: Map<String, String>

    init {
        val metadata = channelResource.metadata
        labels = metadata.labels
    }

    override fun test(agentResource: AgentResource): Boolean {
        val channelSubset = labels[DEPLOYMENT_SUBSET_LABEL_KEY] ?: DEPLOYMENT_SUBSET_LABEL_DEFAULT_VALUE
        val agentSubset = agentResource.metadata.labels[DEPLOYMENT_SUBSET_LABEL_KEY] ?: DEPLOYMENT_SUBSET_LABEL_DEFAULT_VALUE
        if (channelSubset != agentSubset) {
            return false
        }

        val supportedTenants = agentResource.spec?.supportedTenants
        val tenantMatches =
            supportedTenants.isNullOrEmpty() ||
                supportedTenants.contains(labels["tenant"])

        val channelMatches = agentResource.spec?.supportedChannels?.contains(labels["channel"])

        return tenantMatches && channelMatches == true
    }
}
