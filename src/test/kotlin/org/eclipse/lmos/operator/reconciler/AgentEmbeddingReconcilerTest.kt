/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler

import io.javaoperatorsdk.operator.api.reconciler.Context
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lmos.classifier.core.Agent
import org.eclipse.lmos.classifier.core.SystemContext
import org.eclipse.lmos.classifier.core.semantic.EmbeddingHandler
import org.eclipse.lmos.operator.resources.AgentResource
import org.eclipse.lmos.operator.resources.AgentSpec
import org.eclipse.lmos.operator.resources.ProvidedCapability
import org.junit.jupiter.api.Test

class AgentEmbeddingReconcilerTest {
    private val embeddingHandler = mockk<EmbeddingHandler>()
    private val underTest = AgentEmbeddingReconciler(embeddingHandler, EmbeddingProperties())

    private val agentResource =
        AgentResource().apply {
            metadata.name = "ordering-agent"
            metadata.namespace = "default"
            spec =
                AgentSpec(
                    id = "ordering-agent-id",
                    description = "Ordering agent description",
                    supportedTenants = setOf("myTenant"),
                    supportedChannels = setOf("myChannel", "anotherChannel"),
                    providedCapabilities =
                        setOf(
                            ProvidedCapability(
                                id = "ordering-cap-id-1",
                                name = "Ordering",
                                version = "1.0",
                                description = "Order description",
                                examples = listOf("example"),
                            ),
                        ),
                )
        }

    private val agentResourceWithEmptyDescriptionAndExamples =
        AgentResource().apply {
            metadata.name = "ordering-agent"
            metadata.namespace = "default"
            spec =
                AgentSpec(
                    id = "ordering-agent-id",
                    description = "Ordering agent description",
                    supportedTenants = setOf("myTenant"),
                    supportedChannels = setOf("myChannel"),
                    providedCapabilities =
                        setOf(
                            ProvidedCapability(
                                id = "ordering-cap-id-1",
                                name = "Ordering with empty description",
                                version = "1.0",
                                description = "",
                                examples = listOf("example"),
                            ),
                            ProvidedCapability(
                                id = "ordering-cap-id-2",
                                name = "Ordering with empty examples",
                                version = "1.0",
                                description = "Order description",
                                examples = listOf(""),
                            ),
                        ),
                )
        }

    @Test
    fun `reconcile ingests embeddings for agent correctly`() {
        // given
        every { embeddingHandler.ingest(any<SystemContext>(), any<Agent>()) } just Runs

        // when
        val result = underTest.reconcile(agentResource, mockk<Context<AgentResource>>())

        // then
        assertThat(result.isNoUpdate).isTrue()

        verify(exactly = 1) {
            // calling with system context myTenant/myChannel
            embeddingHandler.ingest(
                match<SystemContext> { it.tenantId == "myTenant" && it.channelId == "myChannel" && it.subset == "stable" },
                match<Agent> { agent ->
                    agent.id == "ordering-agent-id" &&
                        agent.capabilities.any {
                            it.id == "ordering-cap-id-1" &&
                                it.description == "Order description" &&
                                it.examples.contains("example")
                        }
                },
            )
        }

        verify {
            // calling with system context myTenant/anotherChannel
            embeddingHandler.ingest(
                match<SystemContext> { it.tenantId == "myTenant" && it.channelId == "anotherChannel" && it.subset == "stable" },
                match<Agent> { agent ->
                    agent.id == "ordering-agent-id" &&
                        agent.capabilities.any {
                            it.id == "ordering-cap-id-1" &&
                                it.description == "Order description" &&
                                it.examples.contains("example")
                        }
                },
            )
        }
    }

    @Test
    fun `reconcile skips capabilities with empty description or examples`() {
        // given
        every { embeddingHandler.ingest(any<SystemContext>(), any<Agent>()) } just Runs

        // when
        val result = underTest.reconcile(agentResourceWithEmptyDescriptionAndExamples, mockk<Context<AgentResource>>())

        // then
        assertThat(result.isNoUpdate).isTrue()

        verify(exactly = 0) {
            embeddingHandler.ingest(
                match<SystemContext> { it.tenantId == "myTenant" && it.channelId == "myChannel" && it.subset == "stable" },
                match<Agent> { it.id == "ordering-agent-id" },
            )
        }
    }

    @Test
    fun `reconcile skips ingest for blacklisted tenants and channels`() {
        // given
        val embeddingProperties =
            EmbeddingProperties(
                blacklist =
                    EmbeddingProperties.Blacklist(
                        tenants = setOf("myTenant"),
                        channels = setOf("anotherChannel"),
                    ),
            )
        val underTest = AgentEmbeddingReconciler(embeddingHandler, embeddingProperties)
        every { embeddingHandler.ingest(any<SystemContext>(), any<Agent>()) } just Runs

        // when
        val result = underTest.reconcile(agentResource, mockk<Context<AgentResource>>())

        // then
        assertThat(result.isNoUpdate).isTrue()

        verify(exactly = 0) {
            embeddingHandler.ingest(
                match<SystemContext> { it.tenantId == "myTenant" && it.channelId == "myChannel" },
                any<Agent>(),
            )
        }

        verify(exactly = 0) {
            embeddingHandler.ingest(
                match<SystemContext> { it.tenantId == "myTenant" && it.channelId == "anotherChannel" },
                any<Agent>(),
            )
        }
    }

    @Test
    fun `cleanup removes embeddings for agent`() {
        // given
        every { embeddingHandler.remove(any(), any()) } just Runs

        // when
        val result = underTest.cleanup(agentResource, mockk())

        // then
        assertThat(result).isNotNull
        verify {
            embeddingHandler.remove(
                match<SystemContext> { it.tenantId == "myTenant" && it.channelId == "myChannel" && it.subset == "stable" },
                match<Agent> { it.id == "ordering-agent-id" },
            )
        }
    }
}
