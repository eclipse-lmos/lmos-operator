/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler.generator

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.api.model.apps.Deployment
import org.eclipse.lmos.operator.reconciler.AgentSpecification
import org.eclipse.lmos.operator.resources.AgentResource
import org.eclipse.lmos.operator.resources.AgentSpec
import org.eclipse.lmos.operator.resources.ProvidedCapability

object AgentGenerator {
    fun createAgentResource(
        deployment: Deployment,
        agentSpecification: AgentSpecification,
    ): AgentResource {
        val agentMetadata =
            ObjectMetaBuilder()
                .withName(deployment.metadata.name)
                .withNamespace(deployment.metadata.namespace)
                .addToLabels("lmos-agent", "true")
                .build()

        val agentSpec =
            AgentSpec(
                id = agentSpecification.id,
                description = agentSpecification.description,
                supportedTenants = agentSpecification.supportedTenants,
                supportedChannels = agentSpecification.supportedChannels,
                providedCapabilities =
                    agentSpecification.capabilities
                        .map {
                            ProvidedCapability(
                                id = it.id,
                                name = it.name,
                                version = it.version,
                                description = it.description,
                                examples = it.examples,
                            )
                        }.toSet(),
            )
        val agentResource = AgentResource()
        agentResource.metadata = agentMetadata
        agentResource.spec = agentSpec

        return agentResource
    }
}
