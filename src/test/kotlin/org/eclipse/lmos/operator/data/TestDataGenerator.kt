/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.data

import org.eclipse.lmos.operator.resolver.ResolveStrategy
import org.eclipse.lmos.operator.resolver.Wire
import org.eclipse.lmos.operator.resources.AgentResource
import org.eclipse.lmos.operator.resources.AgentSpec
import org.eclipse.lmos.operator.resources.ChannelResource
import org.eclipse.lmos.operator.resources.ChannelSpec
import org.eclipse.lmos.operator.resources.ProvidedCapability
import org.eclipse.lmos.operator.resources.RequiredCapability

object TestDataGenerator {
    fun createAgentResource(version: String): AgentResource {
        val agentResource = AgentResource()
        val agentSpec =
            AgentSpec(
                "agentId",
                "description",
                setOf("tenant1", "tenant2"),
                setOf("channel1", "channel2"),
                setOf(ProvidedCapability("Capability1Id", "Capability1", version, "Capability description")),
            )
        agentResource.spec = agentSpec
        return agentResource
    }

    fun createAgentResourceWithMultipleCapabilities(
        version1: String,
        version2: String,
    ): AgentResource {
        val agentResource = AgentResource()
        val agentSpec =
            AgentSpec(
                "agentId",
                "description",
                setOf("tenant1", "tenant2"),
                setOf("channel1", "channel2"),
                setOf(
                    ProvidedCapability("Capability1Id", "Capability1", version1, "Capability1 description"),
                    ProvidedCapability("Capability2Id", "Capability2", version2, "Capability2 description"),
                ),
            )
        agentResource.spec = agentSpec
        return agentResource
    }

    fun createTestChannelResource(version: String): ChannelResource {
        val channelResource = ChannelResource()

        val requiredCapabilities: MutableSet<RequiredCapability> = HashSet()
        requiredCapabilities.add(RequiredCapability("Capability1Id", "Capability1", version, ResolveStrategy.HIGHEST))

        val channelSpec = ChannelSpec(requiredCapabilities)
        channelResource.spec = channelSpec

        return channelResource
    }

    fun createTestChannelResourceWithMultipleCapabilities(
        version1: String,
        version2: String,
        strategy: ResolveStrategy,
    ): ChannelResource {
        val channelResource = ChannelResource()

        val requiredCapabilities: MutableSet<RequiredCapability> = HashSet()
        requiredCapabilities.add(RequiredCapability("Capability1Id", "Capability1", version1, strategy))
        requiredCapabilities.add(RequiredCapability("Capability2Id", "Capability2", version2, strategy))

        val channelSpec = ChannelSpec(requiredCapabilities)
        channelResource.spec = channelSpec

        return channelResource
    }

    fun createRequiredCapability(): RequiredCapability = RequiredCapability("Capability1Id", "capability1", ">=1.0.0")

    fun createProvidedCapability(): ProvidedCapability = ProvidedCapability("Capability1Id", "capability1", "1.2.0", "description")

    fun createAgentWire(): Wire<AgentResource> = Wire(createRequiredCapability(), createProvidedCapability(), createAgentResource("1.1.0"))
}
