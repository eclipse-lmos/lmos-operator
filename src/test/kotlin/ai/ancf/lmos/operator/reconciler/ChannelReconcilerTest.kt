/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.utils.Serialization
import io.javaoperatorsdk.operator.springboot.starter.test.EnableMockOperator
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.eclipse.lmos.operator.OperatorApplication
import org.eclipse.lmos.operator.resources.AgentResource
import org.eclipse.lmos.operator.resources.ChannelResource
import org.eclipse.lmos.operator.resources.ChannelRoutingResource
import org.eclipse.lmos.operator.resources.ResolveStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.util.ResourceUtils
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

@SpringBootTest(
    properties = ["spring.main.allow-bean-definition-overriding=true"],
    classes = [OperatorApplication::class],
)
@EnableMockOperator(
    crdPaths = [
        "classpath:META-INF/fabric8/channels.lmos.eclipse-v1.yml",
        "classpath:META-INF/fabric8/agents.lmos.eclipse-v1.yml",
        "classpath:META-INF/fabric8/channelroutings.lmos.eclipse-v1.yml",
        "classpath:META-INF/fabric8/channelrollouts.lmos.eclipse-v1.yml",
    ],
)
internal class ChannelReconcilerTest {
    @Autowired
    lateinit var client: KubernetesClient

    @AfterEach
    fun cleanUp() {
        client.resources(AgentResource::class.java).delete()
        client.resources(ChannelResource::class.java).delete()
        client.resources(ChannelRoutingResource::class.java).delete()
    }

    @Test
    fun verifyThatCRDAreCreated() {
        assertThat(
            client
                .apiextensions()
                .v1()
                .customResourceDefinitions()
                .withName("agents.lmos.eclipse")
                .get(),
        )
            .isNotNull()

        assertThat(
            client
                .apiextensions()
                .v1()
                .customResourceDefinitions()
                .withName("channels.lmos.eclipse")
                .get(),
        )
            .isNotNull()
    }

    @Test
    fun shouldCreateResolvedChannelRouting() {
        // When I create two Agents

        client.load(getResource("acme-billing-agent-v1.yaml")).createOrReplace()
        client.load(getResource("contract-agent-v1.yaml")).createOrReplace()

        // When I create a Channel the Reconciler should start
        client.load(getResource("acme-ivr-channel-v1.yaml")).createOrReplace()

        // Then the channel status should be updated to resolved
        val channelResources =
            Awaitility.await().atMost(5, TimeUnit.SECONDS) // Timeout duration
                .pollInterval(50, TimeUnit.MILLISECONDS) // Polling interval
                .until(
                    {
                        client.resources(
                            ChannelResource::class.java,
                        ).list().items
                    },
                    { c: List<ChannelResource> -> c[0].status != null },
                )

        assertThat(channelResources).isNotNull().hasSize(1)
        assertThat(channelResources[0].status.resolveStatus).isEqualTo(ResolveStatus.RESOLVED)
        assertThat(channelResources[0].status.unresolvedRequiredCapabilities).isEmpty()

        // And the channel routing resource should be created and status is resolved
        var channelRoutingResources =
            Awaitility.await().atMost(5, TimeUnit.SECONDS) // Timeout duration
                .pollInterval(50, TimeUnit.MILLISECONDS) // Polling interval
                .until(
                    {
                        client.resources(
                            ChannelRoutingResource::class.java,
                        ).list().items
                    },
                    { r: List<ChannelRoutingResource> -> r.isNotEmpty() },
                )

        assertThat(channelRoutingResources).hasSize(1)
        val channelRoutingResource = channelRoutingResources[0]
        // assertThat(channelRoutingResource.getStatus().getResolveStatus()).isEqualTo(ResolveStatus.RESOLVED);
        assertThat(channelRoutingResource.metadata.name).isEqualTo("acme-ivr-stable")
        assertThat(channelRoutingResource.metadata.ownerReferences).hasSize(1)
        println(Serialization.asYaml(channelRoutingResource))

        // If I delete the ChannelRoutingResource the desired state is reconciled again
        client.resources(ChannelRoutingResource::class.java).delete()
        channelRoutingResources =
            Awaitility.await().atMost(5, TimeUnit.SECONDS) // Timeout duration
                .pollInterval(50, TimeUnit.MILLISECONDS) // Polling interval
                .until(
                    {
                        client.resources(
                            ChannelRoutingResource::class.java,
                        ).list().items
                    },
                    { r: List<ChannelRoutingResource> -> r.isNotEmpty() },
                )

        assertThat(channelRoutingResources).hasSize(1)
    }

    @Test
    fun shouldCreateUnresolvedChannelRouting() {
        // When I create an Agent and a Channel

        client.load(getResource("acme-billing-agent-v1.yaml")).createOrReplace()
        client.load(getResource("acme-web-channel-v1.yaml")).createOrReplace()

        // Then the channel status should be updated to unresolved
        val channelResources =
            Awaitility.await().atMost(5, TimeUnit.SECONDS) // Timeout duration
                .pollInterval(50, TimeUnit.MILLISECONDS) // Polling interval
                .until(
                    {
                        client.resources(
                            ChannelResource::class.java,
                        ).list().items
                    },
                    { c: List<ChannelResource> -> c[0].status != null },
                )

        assertThat(channelResources).isNotNull().hasSize(1)
        assertThat(channelResources[0].status.resolveStatus).isEqualTo(ResolveStatus.UNRESOLVED)
        assertThat(channelResources[0].status.unresolvedRequiredCapabilities).isNotEmpty()

        // And the channel routing resource should be created but status is unresolved
        val channelRoutingResources =
            Awaitility.await().atMost(5, TimeUnit.SECONDS) // Timeout duration
                .pollInterval(50, TimeUnit.MILLISECONDS) // Polling interval
                .until(
                    {
                        client.resources(
                            ChannelRoutingResource::class.java,
                        ).list().items
                    },
                    { r: List<ChannelRoutingResource> -> r.isNotEmpty() },
                )

        assertThat(channelRoutingResources).isNotNull().hasSize(1)
        val channelRoutingResource = channelRoutingResources[0]
        // assertThat(channelRoutingResource.getStatus().getResolveStatus()).isEqualTo(ResolveStatus.UNRESOLVED);
        assertThat(channelRoutingResource.metadata.name).isEqualTo("acme-web-stable")
        assertThat(channelRoutingResource.metadata.ownerReferences).hasSize(1)
    }

    @Test
    fun shouldCreateUnresolvedChannelRoutingForNotMatchingTenant() {
        // When I create an Agent and a Channel with different tenants

        client.load(getResource("acme-billing-agent-v1.yaml")).createOrReplace()
        client.load(getResource("globex-web-channel-v1.yaml")).createOrReplace()

        // Then the channel status should be updated to unresolved
        val channelResources =
            Awaitility.await().atMost(5, TimeUnit.SECONDS) // Timeout duration
                .pollInterval(50, TimeUnit.MILLISECONDS) // Polling interval
                .until(
                    {
                        client.resources(
                            ChannelResource::class.java,
                        ).list().items
                    },
                    { c: List<ChannelResource> -> c[0].status != null },
                )

        assertThat(channelResources).isNotNull().hasSize(1)
        assertThat(channelResources[0].status.resolveStatus).isEqualTo(ResolveStatus.UNRESOLVED)
        assertThat(channelResources[0].status.unresolvedRequiredCapabilities).isNotEmpty()

        // And the channel routing resource should be created but status is unresolved
        val channelRoutingResources =
            Awaitility.await().atMost(5, TimeUnit.SECONDS) // Timeout duration
                .pollInterval(50, TimeUnit.MILLISECONDS) // Polling interval
                .until(
                    {
                        client.resources(
                            ChannelRoutingResource::class.java,
                        ).list().items
                    },
                    { r: List<ChannelRoutingResource> -> r.isNotEmpty() },
                )

        assertThat(channelRoutingResources).isNotNull().hasSize(1)
        val channelRoutingResource = channelRoutingResources[0]
        // assertThat(channelRoutingResource.getStatus().getResolveStatus()).isEqualTo(ResolveStatus.UNRESOLVED);
        assertThat(channelRoutingResource.metadata.name).isEqualTo("globex-web-stable")
        assertThat(channelRoutingResource.metadata.ownerReferences).hasSize(1)
    }

    private fun getResource(resourceName: String): FileInputStream {
        return FileInputStream(ResourceUtils.getFile("classpath:$resourceName"))
    }
}
