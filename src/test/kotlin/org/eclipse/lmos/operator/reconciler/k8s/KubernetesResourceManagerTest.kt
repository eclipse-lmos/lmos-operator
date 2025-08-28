/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler.k8s

import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.PodSpec
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.ServiceList
import io.fabric8.kubernetes.api.model.ServicePort
import io.fabric8.kubernetes.api.model.ServiceSpec
import io.fabric8.kubernetes.api.model.StatusDetails
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.MixedOperation
import io.fabric8.kubernetes.client.dsl.Resource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.lmos.operator.resources.AgentResource
import org.junit.jupiter.api.Test

class KubernetesResourceManagerTest {
    private val kubernetesClient = mockk<KubernetesClient>()
    private val underTest = KubernetesResourceManager(kubernetesClient)

    @Test
    fun `createAgentResource creates resource`() {
        // given
        val agent = AgentResource()
        val resourceOp = mockk<MixedOperation<AgentResource, KubernetesResourceList<AgentResource>, Resource<AgentResource>>>()
        every { kubernetesClient.resources(AgentResource::class.java) } returns resourceOp
        every { resourceOp.createOrReplace(agent) } returns agent

        // when
        underTest.createAgentResource(agent)

        // then
        verify { resourceOp.createOrReplace(agent) }
    }

    @Test
    fun `deleteAgentResource deletes resource`() {
        // given
        val deployment =
            Deployment().apply {
                metadata =
                    ObjectMeta().apply {
                        name = "agent"
                        namespace = "my-namespace"
                    }
            }

        val resource = mockk<Resource<AgentResource>>()
        every {
            kubernetesClient
                .resources(AgentResource::class.java)
                .inNamespace("my-namespace")
                .withName("agent")
        } returns resource
        every { resource.delete() } returns listOf(StatusDetails())

        // when
        underTest.deleteAgentResource(deployment)

        // then
        verify { resource.delete() }
    }

    @Test
    fun `isDeploymentReady returns true if replicas and availableReplicas match desired`() {
        // given
        val deployment =
            Deployment().apply {
                spec = DeploymentSpec().apply { replicas = 2 }
                status =
                    DeploymentStatus().apply {
                        replicas = 2
                        availableReplicas = 2
                    }
            }

        // when-then
        assertThat(underTest.isDeploymentReady(deployment)).isTrue()
    }

    @Test
    fun `isDeploymentReady returns false if replicas do not match`() {
        // given
        val deployment =
            Deployment().apply {
                spec = DeploymentSpec().apply { replicas = 2 }
                status =
                    DeploymentStatus().apply {
                        replicas = 1
                        availableReplicas = 1
                    }
            }

        // when-then
        assertThat(underTest.isDeploymentReady(deployment)).isFalse()
    }

    @Test
    fun `getServiceUrl returns correct url`() {
        // given
        val deployment =
            Deployment().apply {
                metadata =
                    ObjectMeta().apply {
                        name = "my-deployment"
                        namespace = "my-namespace"
                    }
                spec =
                    DeploymentSpec().apply {
                        template =
                            PodTemplateSpecBuilder()
                                .withNewMetadata()
                                .addToLabels("app", "test")
                                .endMetadata()
                                .withSpec(PodSpec())
                                .build()
                    }
            }

        val service =
            Service().apply {
                metadata =
                    ObjectMeta().apply {
                        name = "my-service"
                        namespace = "my-namespace"
                    }
                spec =
                    ServiceSpec().apply {
                        selector = mapOf("app" to "test") // matches deployment pod labels
                        ports =
                            listOf(
                                ServicePort().apply {
                                    port = 8080
                                    appProtocol = "http"
                                },
                            )
                    }
            }

        val serviceList = ServiceList().apply { items = listOf(service) }
        every { kubernetesClient.services().inNamespace("my-namespace").list() } returns serviceList

        // when
        val url = underTest.getServiceUrl(deployment, "/capabilities")

        // then
        assertThat(url).isEqualTo("http://my-service.my-namespace.svc.cluster.local:8080/capabilities")
    }

    @Test
    fun `getServiceUrl throws IllegalStateException if no matching service is found`() {
        // given
        val deployment =
            Deployment().apply {
                metadata =
                    ObjectMeta().apply {
                        name = "my-deployment"
                        namespace = "my-namespace"
                    }
                spec =
                    DeploymentSpec().apply {
                        template =
                            PodTemplateSpecBuilder()
                                .withNewMetadata()
                                .addToLabels("app", "test")
                                .endMetadata()
                                .withSpec(PodSpec())
                                .build()
                    }
            }

        val service =
            Service().apply {
                metadata =
                    ObjectMeta().apply {
                        name = "my-service"
                        namespace = "my-namespace"
                    }
                spec =
                    ServiceSpec().apply {
                        selector = mapOf("something" to "else") // no match for this selector
                        ports =
                            listOf(
                                ServicePort().apply {
                                    port = 8080
                                    appProtocol = "http"
                                },
                            )
                    }
            }

        val serviceList = ServiceList().apply { items = listOf(service) }
        every { kubernetesClient.services().inNamespace("my-namespace").list() } returns serviceList

        // when-then
        assertThatThrownBy { underTest.getServiceUrl(deployment, "/capabilities") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Expected exactly one service for deployment my-deployment, but got 0")
    }
}
