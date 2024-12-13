/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler.generator

import io.fabric8.kubernetes.api.model.ObjectMeta
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lmos.operator.reconciler.generator.RoutingChannelGenerator.createChannelRoutingResource
import org.eclipse.lmos.operator.resolver.Wire
import org.eclipse.lmos.operator.resources.AgentResource
import org.eclipse.lmos.operator.resources.AgentSpec
import org.eclipse.lmos.operator.resources.CapabilityGroup
import org.eclipse.lmos.operator.resources.ChannelResource
import org.eclipse.lmos.operator.resources.ProvidedCapability
import org.eclipse.lmos.operator.resources.RequiredCapability
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChannelRoutingGeneratorTest {
    private lateinit var wiredCapabilities: Set<Wire<AgentResource>>
    private lateinit var agent1: AgentResource
    private lateinit var agent2: AgentResource

    @BeforeEach
    fun setUp() {
        agent1 = createAgentResource("Agent 1")
        agent2 = createAgentResource("Agent 2")

        wiredCapabilities =
            setOf(
                Wire(
                    requiredCapability = RequiredCapability("capability1", "1.0.0"),
                    providedCapability = ProvidedCapability("capability1", "1.0.0", "bla"),
                    provider = agent1,
                ),
                Wire(
                    requiredCapability = RequiredCapability("capability2", "1.0.0"),
                    providedCapability = ProvidedCapability("capability2", "1.0.0", "bla"),
                    provider = agent1,
                ),
                Wire(
                    requiredCapability = RequiredCapability("capability3", "1.0.0"),
                    providedCapability = ProvidedCapability("capability3", "1.0.0", "bla"),
                    provider = agent2,
                ),
            )
    }

    @Test
    fun testGroupWiresByProvider() {
        val groupedWires = groupWiresByProvider(wiredCapabilities)

        assertThat(groupedWires).hasSize(2)

        assertThat(groupedWires)
            .hasSize(2)
            .containsOnlyKeys(agent1, agent2)

        assertThat(groupedWires[agent1]).hasSize(2)
        for (wire in groupedWires[agent1]!!) {
            assertThat(wire.providedCapability.name).isIn("capability1", "capability2")
        }

        assertThat(groupedWires[agent2]).hasSize(1)
        for (wire in groupedWires[agent2]!!) {
            assertThat(wire.providedCapability.name).isEqualTo("capability3")
        }
    }

    @Test
    fun testCreateChannelRoutingResource() {
        val metadata = ObjectMeta()
        metadata.name = "testChannel"
        metadata.namespace = "testNamespace"
        metadata.labels =
            mapOf(
                CHANNEL to "testChannel",
                TENANT to "testTenant",
                VERSION to "1.0",
                SUBSET to "testSubset",
            )
        metadata.uid = "testUid"

        val channelResource = ChannelResource()
        channelResource.metadata = metadata

        val channelRoutingResource = createChannelRoutingResource(channelResource, wiredCapabilities, "testSubset")

        assertThat(channelRoutingResource).isNotNull()
        assertThat(channelRoutingResource.metadata).isNotNull()
        assertThat(channelRoutingResource.metadata.name).isEqualTo(metadata.name)
        assertThat(channelRoutingResource.metadata.namespace).isEqualTo(metadata.namespace)
        assertThat(channelRoutingResource.metadata.labels)
            .containsEntry(CHANNEL, "testChannel")
            .containsEntry(TENANT, "testTenant")
            .containsEntry(VERSION, "1.0")
            .containsEntry(SUBSET, "testSubset")
        assertThat(channelRoutingResource.hasOwnerReferenceFor(channelResource)).isTrue()

        val capabilityGroups: Set<CapabilityGroup> = channelRoutingResource.spec.capabilityGroups

        assertThat(capabilityGroups).hasSize(2)
        assertThat(capabilityGroups).extracting<String> { it.name }.containsExactlyInAnyOrder("Agent 1", "Agent 2")

        for (capabilityGroup in capabilityGroups) {
            if (capabilityGroup.name == "Agent 1") {
                assertThat(capabilityGroup.capabilities).hasSize(2)
                for (capability in capabilityGroup.capabilities) {
                    assertThat(capability.description).isEqualTo("bla")
                }
            } else if (capabilityGroup.name == "Agent 2") {
                assertThat(capabilityGroup.capabilities).hasSize(1)
                for (capability in capabilityGroup.capabilities) {
                    assertThat(capability.description).isEqualTo("bla")
                }
            }
        }
    }

    companion object {
        private const val CHANNEL = "channel"
        private const val TENANT = "tenant"
        private const val VERSION = "version"
        private const val SUBSET = "subset"

        private fun createAgentResource(name: String): AgentResource {
            val agent = AgentResource()
            val objectMeta = ObjectMeta()
            objectMeta.name = name
            objectMeta.namespace = "testNamespace"
            agent.metadata = objectMeta
            val agentSpec =
                AgentSpec(
                    supportedTenants = emptySet(),
                    supportedChannels = emptySet(),
                    providedCapabilities = emptySet(),
                    description = "Bla",
                )
            agent.spec = agentSpec
            return agent
        }
    }
}
