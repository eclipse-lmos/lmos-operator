/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler

import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lmos.operator.reconciler.client.AgentClient
import org.eclipse.lmos.operator.reconciler.k8s.KubernetesResourceManager
import org.eclipse.lmos.operator.resources.AgentResource
import org.junit.jupiter.api.Test

class AgentDeploymentReconcilerTest {
    private val deployment =
        Deployment().apply {
            metadata = ObjectMeta().apply { name = "ordering-agent" }
        }

    private val capabilities =
        AgentSpecification(
            id = "ordering-agent",
            description = "Ordering agent description",
            supportedTenants = setOf("myTenant"),
            supportedChannels = setOf("myChannel"),
            capabilities =
                listOf(
                    CapabilitySpecification(
                        id = "ordering-cap-id",
                        name = "Ordering",
                        version = "1.0",
                        description = "Order description",
                        examples = listOf("example"),
                    ),
                ),
        )

    private val kubernetesResourceManager = mockk<KubernetesResourceManager>()
    private val agentClient = mockk<AgentClient>()
    private val underTest = AgentDeploymentReconciler(kubernetesResourceManager, agentClient)

    @Test
    fun `reconcile creates agent resource when deployment is ready`() {
        // given
        every { kubernetesResourceManager.isDeploymentReady(deployment) } returns true
        every { kubernetesResourceManager.getServiceUrl(deployment, ".well-known/capabilities.json") } returns "http://dummy-url"
        every { kubernetesResourceManager.createAgentResource(any()) } just Runs
        every { agentClient.get("http://dummy-url", AgentSpecification::class.java) } returns capabilities

        // when
        val result = underTest.reconcile(deployment, mockk<Context<Deployment>>())

        // then
        assertThat(result.isNoUpdate).isTrue()

        verify {
            kubernetesResourceManager.createAgentResource(
                match<AgentResource> { resource ->
                    resource.spec.id == "ordering-agent" &&
                        resource.spec.description == "Ordering agent description" &&
                        resource.spec.supportedTenants.containsAll(listOf("myTenant")) &&
                        resource.spec.supportedChannels.containsAll(listOf("myChannel")) &&
                        resource.spec.providedCapabilities.size == 1 &&
                        resource.spec.providedCapabilities.first().let { capability ->
                            capability.id == "ordering-cap-id" &&
                                capability.name == "Ordering" &&
                                capability.version == "1.0" &&
                                capability.description == "Order description" &&
                                capability.examples.contains("example")
                        }
                },
            )
        }
    }

    @Test
    fun `reconcile reschedules when deployment is not ready`() {
        // given
        every { kubernetesResourceManager.isDeploymentReady(deployment) } returns false

        // when
        val result = underTest.reconcile(deployment, mockk<Context<Deployment>>())

        // then
        verify(exactly = 0) { kubernetesResourceManager.createAgentResource(any()) }
        assertThat(result.scheduleDelay.get()).isEqualTo(10000L)
    }

    @Test
    fun `reconcile throws IllegalStateException if service URL cannot be determined`() {
        // given
        every { kubernetesResourceManager.isDeploymentReady(deployment) } returns true
        every { kubernetesResourceManager.getServiceUrl(deployment, any()) } throws RuntimeException("URL failure")

        // when / then
        assertThatThrownBy { underTest.reconcile(deployment, mockk<Context<Deployment>>()) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Failed to create agent resource for deployment 'ordering-agent'")
    }

    @Test
    fun `reconcile throws IllegalStateException if call to well-known endpoint fails`() {
        // given
        every { kubernetesResourceManager.isDeploymentReady(deployment) } returns true
        every { kubernetesResourceManager.getServiceUrl(deployment, any()) } returns "http://dummy-url"
        every { agentClient.get("http://dummy-url", AgentSpecification::class.java) } throws RuntimeException("Agent failure")

        // when / then
        assertThatThrownBy { underTest.reconcile(deployment, mockk<Context<Deployment>>()) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Failed to create agent resource for deployment 'ordering-agent'")
    }

    @Test
    fun `cleanup should delete agent resource`() {
        // given
        every { kubernetesResourceManager.deleteAgentResource(deployment) } just Runs

        // when
        underTest.cleanup(deployment, null)

        // then
        verify { kubernetesResourceManager.deleteAgentResource(deployment) }
    }
}
