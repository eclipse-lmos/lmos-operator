/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.utils.Serialization
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.eclipse.lmos.operator.EnableMockOperator
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
        client.load(getResource("acme-contract-agent-v1.yaml")).createOrReplace()

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

    @Test
    fun `should resolve Channel again if Agent was added later`() {
        // When I add one Agent
        client.load(getResource("acme-billing-agent-v1.yaml")).createOrReplace()
        // ... and a Channel
        client.load(getResource("acme-ivr-channel-v1.yaml")).createOrReplace()

        // Then the Channel is UNRESOLVED, because the required capabilities are not present
        var channelResources = Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .pollInterval(50, TimeUnit.MILLISECONDS)
            .until(
                { client.resources(ChannelResource::class.java).list().items },
                { c: List<ChannelResource> -> c[0].status != null },
            )
        assertThat(channelResources).isNotNull().hasSize(1)
        assertThat(channelResources[0].status.resolveStatus).isEqualTo(ResolveStatus.UNRESOLVED)
        assertThat(channelResources[0].status.unresolvedRequiredCapabilities).isNotEmpty()

        // When I add another Agent with the missing capabilities
        client.load(getResource("acme-contract-agent-v1.yaml")).createOrReplace()

        // Then the Channel is RESOLVED, because all required capabilities are present
        channelResources = Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .pollInterval(50, TimeUnit.MILLISECONDS)
            .until(
                { client.resources(ChannelResource::class.java).list().items },
                { c: List<ChannelResource> -> c[0].status.resolveStatus != ResolveStatus.UNRESOLVED },
            )
        assertThat(channelResources).isNotNull().hasSize(1)
        assertThat(channelResources[0].status.resolveStatus).isEqualTo(ResolveStatus.RESOLVED)
        assertThat(channelResources[0].status.unresolvedRequiredCapabilities).isEmpty()
    }

    @Test
    fun `should resolve Channel again if Agent was removed`() {
        // When I create two Agents
        client.load(getResource("acme-billing-agent-v1.yaml")).createOrReplace()
        client.load(getResource("acme-contract-agent-v1.yaml")).createOrReplace()
        // ... and a Channel
        client.load(getResource("acme-ivr-channel-v1.yaml")).createOrReplace()

        // Then the Channel is RESOLVED, because all required capabilities are present
        var channelResources = Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .pollInterval(50, TimeUnit.MILLISECONDS)
            .until(
                { client.resources(ChannelResource::class.java).list().items },
                { c: List<ChannelResource> -> c[0].status != null },
            )
        assertThat(channelResources).isNotNull().hasSize(1)
        assertThat(channelResources[0].status.resolveStatus).isEqualTo(ResolveStatus.RESOLVED)
        assertThat(channelResources[0].status.unresolvedRequiredCapabilities).isEmpty()

        // When I remove one Agent
        client.load(getResource("acme-contract-agent-v1.yaml")).delete()

        // Then the Channel is UNRESOLVED, because the required capabilities are not present anymore
        channelResources = Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .pollInterval(50, TimeUnit.MILLISECONDS)
            .until(
                { client.resources(ChannelResource::class.java).list().items },
                { c: List<ChannelResource> -> c[0].status.resolveStatus != ResolveStatus.RESOLVED },
            )
        assertThat(channelResources).isNotNull().hasSize(1)
        assertThat(channelResources[0].status.resolveStatus).isEqualTo(ResolveStatus.UNRESOLVED)
        assertThat(channelResources[0].status.unresolvedRequiredCapabilities).isNotEmpty()
    }


    @Test
    fun `should resolve Channel again if Agent was updated`() {
        // When I create two Agents
        client.load(getResource("acme-billing-agent-v1.yaml")).createOrReplace()
        client.load(getResource("acme-contract-agent-v1.yaml")).createOrReplace()
        // ... and a Channel
        client.load(getResource("acme-ivr-channel-v1.yaml")).createOrReplace()

        // Then the Channel is RESOLVED, because the required capabilities are present
        var channelResources = Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .pollInterval(50, TimeUnit.MILLISECONDS)
            .until(
                { client.resources(ChannelResource::class.java).list().items },
                { c: List<ChannelResource> -> c[0].status != null },
            )
        assertThat(channelResources).isNotNull().hasSize(1)
        assertThat(channelResources[0].status.resolveStatus).isEqualTo(ResolveStatus.RESOLVED)
        assertThat(channelResources[0].status.unresolvedRequiredCapabilities).isEmpty()

        // When I update one Agent that no longer contains the required capability
        client.load(getResource("acme-contract-agent-v2.yaml")).createOrReplace()

        // Then the Channel is UNRESOLVED, because the required capability is not present anymore
        channelResources = Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .pollInterval(50, TimeUnit.MILLISECONDS)
            .until(
                { client.resources(ChannelResource::class.java).list().items },
                { c: List<ChannelResource> -> c[0].status.resolveStatus != ResolveStatus.RESOLVED },
            )
        assertThat(channelResources).isNotNull().hasSize(1)
        assertThat(channelResources[0].status.resolveStatus).isEqualTo(ResolveStatus.UNRESOLVED)
        assertThat(channelResources[0].status.unresolvedRequiredCapabilities).isNotEmpty()
    }

    @Test
    fun `should update Channel Routing capabilities if Agent was updated with new capability version`() {
        // When I create two Agents
        client.load(getResource("acme-billing-agent-v1.yaml")).createOrReplace()
        client.load(getResource("acme-contract-agent-v1.yaml")).createOrReplace()
        // ... and a Channel
        client.load(getResource("acme-ivr-channel-v1.yaml")).createOrReplace()

        // Then the Channel is RESOLVED, because the required capabilities are present
        var channelResources = Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .pollInterval(50, TimeUnit.MILLISECONDS)
            .until(
                { client.resources(ChannelResource::class.java).list().items },
                { c: List<ChannelResource> -> c[0].status != null },
            )
        assertThat(channelResources).isNotNull().hasSize(1)
        assertThat(channelResources[0].status.resolveStatus).isEqualTo(ResolveStatus.RESOLVED)
        assertThat(channelResources[0].status.unresolvedRequiredCapabilities).isEmpty()

        // and the Channel Routing contains the capability 'download-bill' in version '1.1.0'
        var channelRoutingResources = Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .pollInterval(50, TimeUnit.MILLISECONDS)
            .until(
                { client.resources(ChannelRoutingResource::class.java).list().items },
                { r: List<ChannelRoutingResource> -> r.isNotEmpty() },
            )
        assertThat(channelRoutingResources).isNotNull()
        var channelRoutingResource = channelRoutingResources.find { it.metadata.name == "acme-ivr-stable" }
        assertThat(channelRoutingResource).isNotNull()
        var capabilityGroup = channelRoutingResource?.spec?.capabilityGroups?.find { it.name == "billing-agent-stable" }
        assertThat(capabilityGroup).isNotNull()
        var capability = capabilityGroup?.capabilities?.find { it.name == "download-bill" }
        assertThat(capability).isNotNull()
        assertThat(capability?.providedVersion).isEqualTo("1.1.0")

        // When I update the Agent 'download-bill' capability to version '1.2.0'
        client.load(getResource("acme-billing-agent-v2.yaml")).createOrReplace()

        // Then the Channel is still RESOLVED
        channelResources = Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .pollInterval(50, TimeUnit.MILLISECONDS)
            .until(
                { client.resources(ChannelResource::class.java).list().items },
                { c: List<ChannelResource> -> c[0].status != null },
            )
        assertThat(channelResources).isNotNull().hasSize(1)
        assertThat(channelResources[0].status.resolveStatus).isEqualTo(ResolveStatus.RESOLVED)
        assertThat(channelResources[0].status.unresolvedRequiredCapabilities).isEmpty()

        // and the Channel Routing contains the capability 'download-bill' in version '1.2.0'
        channelRoutingResources = Awaitility.await().atMost(5, TimeUnit.SECONDS)
            .pollInterval(50, TimeUnit.MILLISECONDS)
            .until(
                { client.resources(ChannelRoutingResource::class.java).list().items },
                { r: List<ChannelRoutingResource> -> r.isNotEmpty() },
            )
        assertThat(channelRoutingResources).isNotNull()
        channelRoutingResource = channelRoutingResources.find { it.metadata.name == "acme-ivr-stable" }
        assertThat(channelRoutingResource).isNotNull()
        capabilityGroup = channelRoutingResource?.spec?.capabilityGroups?.find { it.name == "billing-agent-stable" }
        assertThat(capabilityGroup).isNotNull()
        capability = capabilityGroup?.capabilities?.find { it.name == "download-bill" }
        assertThat(capability).isNotNull()
        assertThat(capability?.providedVersion).isEqualTo("1.2.0")
    }

    private fun getResource(resourceName: String): FileInputStream {
        return FileInputStream(ResourceUtils.getFile("classpath:$resourceName"))
    }

}
