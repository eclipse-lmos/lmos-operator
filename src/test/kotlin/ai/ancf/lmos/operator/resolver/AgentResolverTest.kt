/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.resolver

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lmos.operator.data.TestDataGenerator
import org.eclipse.lmos.operator.resolver.impl.AgentResolveContext
import org.eclipse.lmos.operator.resolver.impl.AgentResolver
import org.junit.jupiter.api.Test

class AgentResolverTest {
    @Test
    fun shouldThrowResolverExceptionResolveContextIsEmpty() {
        // Given the resolve context is empty
        val channelResource = TestDataGenerator.createTestChannelResource(">=1.1.0")
        val resolveContext = AgentResolveContext(listOf())

        // When
        val resolver = AgentResolver()
        val resolverException =
            Assertions.catchThrowableOfType(
                { resolver.resolve(channelResource.spec.requiredCapabilities, resolveContext) },
                ResolverException::class.java,
            )

        // Then
        assertThat(resolverException).hasMessageContaining("Resolve context is empty")
        assertThat(resolverException.getUnresolvedRequiredCapabilities()).hasSize(1)
    }

    @Test
    fun shouldThrowResolverExceptionWhenRequiredCapabilityNotResolved() {
        // Given the channel requires a capability but only an older version is provided
        val channelResource = TestDataGenerator.createTestChannelResource(">=1.1.0")
        val agentResource = TestDataGenerator.createAgentResource("1.0.0")
        val resolveContext = AgentResolveContext(listOf(agentResource))

        // When
        val resolver = AgentResolver()
        val resolverException =
            Assertions.catchThrowableOfType(
                { resolver.resolve(channelResource.spec.requiredCapabilities, resolveContext) },
                ResolverException::class.java,
            )

        // Then
        assertThat(resolverException).hasMessageContaining("Required capabilities not resolved")
        assertThat(resolverException.getUnresolvedRequiredCapabilities()).hasSize(1)
    }

    @Test
    fun shouldThrowResolverExceptionWhenRequiredCapabilityNotResolved2() {
        // Given the channel requires two capabilities but only one is provided
        val channelResource =
            TestDataGenerator.createTestChannelResourceWithMultipleCapabilities(
                "1.1.0",
                "1.2.0",
                ResolveStrategy.HIGHEST,
            )
        val agentResource = TestDataGenerator.createAgentResource("1.1.0")
        val resolveContext = AgentResolveContext(listOf(agentResource))

        // When
        val resolver = AgentResolver()
        val resolverException =
            Assertions.catchThrowableOfType(
                { resolver.resolve(channelResource.spec.requiredCapabilities, resolveContext) },
                ResolverException::class.java,
            )

        // Then
        assertThat(resolverException).hasMessageContaining("Required capabilities not resolved")
        assertThat(resolverException.getUnresolvedRequiredCapabilities()).hasSize(1)
    }

    @Test
    @Throws(ResolverException::class)
    fun shouldWireCapabilityWithHighestVersion() {
        // Given
        val agentResource1 = TestDataGenerator.createAgentResource("1.0.0")
        val agentResource2 = TestDataGenerator.createAgentResource("1.2.0")
        val agentResource3 = TestDataGenerator.createAgentResource("1.1.0")
        val resolveContext = AgentResolveContext(listOf(agentResource1, agentResource2, agentResource3))

        val channelResource = TestDataGenerator.createTestChannelResource(">=1.1.0")

        // When
        val resolver = AgentResolver()
        val wiredCapabilities = resolver.resolve(channelResource.spec.requiredCapabilities, resolveContext)

        // Then
        // One capability is wired with two agents
        assertThat(wiredCapabilities).hasSize(1)
        assertThat(wiredCapabilities.stream().toList()[0].provider).isEqualTo(agentResource2)
    }

    @Test
    @Throws(ResolverException::class)
    fun shouldWireHighestVersions() {
        // Given an agent with two capabilities
        val agentResource1 = TestDataGenerator.createAgentResourceWithMultipleCapabilities("1.1.0", "1.2.0")
        val agentResource2 = TestDataGenerator.createAgentResourceWithMultipleCapabilities("1.0.0", "1.1.0")
        val resolveContext = AgentResolveContext(listOf(agentResource1, agentResource2))

        // Given a channel which requires the two capabilities
        val channelResource =
            TestDataGenerator.createTestChannelResourceWithMultipleCapabilities(
                "1.1.0",
                ">=1.1.0",
                ResolveStrategy.HIGHEST,
            )

        // When
        val resolver = AgentResolver()
        val wiredCapabilities = resolver.resolve(channelResource.spec.requiredCapabilities, resolveContext)

        // Then
        // Two capabilities are wired with a single agent
        assertThat(wiredCapabilities).hasSize(2)
        assertThat(wiredCapabilities.stream().toList()[0].provider).isEqualTo(agentResource1)
        assertThat(wiredCapabilities.stream().toList()[1].provider).isEqualTo(agentResource1)
    }

    @Test
    @Throws(ResolverException::class)
    fun shouldWireMostSimilarVersions() {
        // Given an agent with two capabilities
        val agentResource1 = TestDataGenerator.createAgentResourceWithMultipleCapabilities("1.2.0", "1.2.0")
        val agentResource2 = TestDataGenerator.createAgentResourceWithMultipleCapabilities("1.1.5", "1.1.1")
        val resolveContext = AgentResolveContext(listOf(agentResource1, agentResource2))

        // Given a channel which requires the two capabilities
        val channelResource =
            TestDataGenerator.createTestChannelResourceWithMultipleCapabilities(
                ">=1.1.0",
                ">=1.1.0",
                ResolveStrategy.MOST_SIMILAR,
            )

        // When
        val resolver = AgentResolver()
        val wiredCapabilities = resolver.resolve(channelResource.spec.requiredCapabilities, resolveContext)

        // Then
        // Two capabilities are wired with a single agent
        assertThat(wiredCapabilities).hasSize(2)
        assertThat(wiredCapabilities.stream().toList()[0].provider).isEqualTo(agentResource2)
        assertThat(wiredCapabilities.stream().toList()[1].provider).isEqualTo(agentResource2)
    }
}
