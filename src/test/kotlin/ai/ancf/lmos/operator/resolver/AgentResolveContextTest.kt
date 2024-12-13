/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.resolver

import org.assertj.core.api.Assertions
import org.eclipse.lmos.operator.data.TestDataGenerator
import org.eclipse.lmos.operator.resolver.impl.AgentResolveContext
import org.eclipse.lmos.operator.resources.RequiredCapability
import org.junit.jupiter.api.Test

class AgentResolveContextTest {
    @Test
    fun shouldFindResourceWithSameVersion() {
        // Given
        val agentResource = TestDataGenerator.createAgentResource("1.0.0")

        // When
        val agentResolveContext = AgentResolveContext(listOf(agentResource))
        val wiredCapabilities =
            agentResolveContext.findCapabilityProviders(
                RequiredCapability("Capability1", "1.0.0"),
            )

        // Then
        Assertions.assertThat(wiredCapabilities).hasSize(1)
        Assertions.assertThat(wiredCapabilities).extracting("provider").contains(agentResource)
    }

    @Test
    fun shouldFindResourceWithHigherVersion() {
        // Given
        val agentResource = TestDataGenerator.createAgentResource("1.1.0")

        // When
        val agentResolveContext = AgentResolveContext(listOf(agentResource))
        val wiredCapabilities =
            agentResolveContext.findCapabilityProviders(
                RequiredCapability("Capability1", ">=1.0.0"),
            )

        // Then
        Assertions.assertThat(wiredCapabilities).hasSize(1)
        Assertions.assertThat(wiredCapabilities).extracting("provider").contains(agentResource)
    }

    @Test
    fun shouldNotFindResourceWithLowerVersion() {
        // Given
        val agentResource = TestDataGenerator.createAgentResource("1.0.0")

        // When
        val agentResolveContext = AgentResolveContext(listOf(agentResource))
        val wiredCapabilities =
            agentResolveContext.findCapabilityProviders(
                RequiredCapability("Capability1", "1.1.0"),
            )

        // Then
        Assertions.assertThat(wiredCapabilities).isEmpty()
    }

    @Test
    fun shouldFindTwoResources() {
        // Given
        val agentResource1 = TestDataGenerator.createAgentResource("1.0.0")
        val agentResource2 = TestDataGenerator.createAgentResource("1.2.0")
        val agentResource3 = TestDataGenerator.createAgentResource("1.1.0")

        // When
        val agentResolveContext = AgentResolveContext(listOf(agentResource1, agentResource2, agentResource3))
        val wiredCapabilities =
            agentResolveContext.findCapabilityProviders(
                RequiredCapability("Capability1", ">=1.1.0"),
            )

        // Then
        Assertions.assertThat(wiredCapabilities).hasSize(2)
        Assertions.assertThat(wiredCapabilities).extracting("provider").contains(agentResource2, agentResource3)
    }

    @Test
    fun shouldDeduplicateSameAgentResource() {
        // Given
        val agentResource1 = TestDataGenerator.createAgentResource("1.1.0")
        val agentResource2 = TestDataGenerator.createAgentResource("1.1.0")

        // When
        val agentResolveContext = AgentResolveContext(listOf(agentResource1, agentResource2))
        val wiredCapabilities =
            agentResolveContext.findCapabilityProviders(
                RequiredCapability("Capability1", "1.1.0"),
            )

        // Then
        Assertions.assertThat(wiredCapabilities).hasSize(1)
        Assertions.assertThat(wiredCapabilities).extracting("provider").contains(agentResource2)
    }

    @Test
    fun shouldNotFindAnyResourceWithNonMatchingCapability() {
        // Given
        val agentResource = TestDataGenerator.createAgentResource("1.0.0")

        // When
        val agentResolveContext = AgentResolveContext(listOf(agentResource))
        val wiredCapabilities =
            agentResolveContext.findCapabilityProviders(
                RequiredCapability("Nonexistent Capability", "1.0.0"),
            )

        // Then
        Assertions.assertThat(wiredCapabilities).isEmpty()
    }

    @Test
    fun shouldFindResourceWithMultipleCapabilities() {
        // Given
        val agentResource = TestDataGenerator.createAgentResourceWithMultipleCapabilities("1.0.0", "2.0.0")

        // When
        val agentResolveContext = AgentResolveContext(listOf(agentResource))
        val wiredCapabilities1 =
            agentResolveContext.findCapabilityProviders(
                RequiredCapability("Capability1", "1.0.0"),
            )
        val wiredCapabilities2 =
            agentResolveContext.findCapabilityProviders(
                RequiredCapability("Capability2", "2.0.0"),
            )

        // Then
        Assertions.assertThat(wiredCapabilities1).hasSize(1).extracting("provider").contains(agentResource)
        Assertions.assertThat(wiredCapabilities2).hasSize(1).extracting("provider").contains(agentResource)
    }
}
