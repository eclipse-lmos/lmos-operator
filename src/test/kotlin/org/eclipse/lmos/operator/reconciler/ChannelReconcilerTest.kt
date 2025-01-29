/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler

import io.fabric8.kubernetes.client.KubernetesClient
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.eclipse.lmos.operator.EnableMockOperator
import org.eclipse.lmos.operator.OperatorApplication
import org.eclipse.lmos.operator.resources.*
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
    fun `verify that CRDs are created`() {
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

        assertThat(
            client
                .apiextensions()
                .v1()
                .customResourceDefinitions()
                .withName("channelroutings.lmos.eclipse")
                .get(),
        )
            .isNotNull()
    }

    @Test
    fun `should create resolved Channel and ChannelRouting`() {
        // When I create two Agents
        client.load(getResource("acme-billing-agent-v1.yaml")).createOrReplace()
        client.load(getResource("acme-contract-agent-v1.yaml")).createOrReplace()
        // ... and a Channel
        client.load(getResource("acme-ivr-channel-v1.yaml")).createOrReplace()

        // Then the Channel is RESOLVED, because the required capabilities are present
        val channelResource = assertThatChannelExists("acme-ivr-stable", ResolveStatus.RESOLVED)
        assertThat(channelResource.status.unresolvedRequiredCapabilities).isEmpty()

        // and the Channel Routing contains the expected capabilities
        val channelRoutingResource = assertThatChannelRoutingExists("acme-ivr-stable")
        assertThat(channelRoutingResource.metadata.ownerReferences).hasSize(1)
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "view-bill", "1.0.0")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "download-bill", "1.1.0")
        assertThatCapabilityExists(channelRoutingResource, "contract-agent", "view-contract", "1.1.0")

        // If I delete the ChannelRoutingResource the desired state is reconciled again
        client.resources(ChannelRoutingResource::class.java).delete()
        assertThatChannelRoutingExists("acme-ivr-stable")
    }

    @Test
    fun `should create unresolved Channel and ChannelRouting in case of not matching capabilities`() {
        // When I create an Agent
        client.load(getResource("acme-billing-agent-v1.yaml")).createOrReplace()
        // ... and a Channel
        client.load(getResource("acme-web-channel-v1.yaml")).createOrReplace()

        // Then the Channel is UNRESOLVED, because the required capability is not present
        val channelResource = assertThatChannelExists("acme-web-stable", ResolveStatus.UNRESOLVED)
        assertThat(channelResource.status.unresolvedRequiredCapabilities).isNotEmpty()

        // and the Channel Routing contains the expected capabilities
        val channelRoutingResource = assertThatChannelRoutingExists("acme-web-stable")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "view-bill", "1.0.0")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "download-bill", "1.1.0")
        assertThatCapabilityGroupNotExists(channelRoutingResource, "contract-agent")
    }

    @Test
    fun `should create unresolved Channel and ChannelRouting in case of not matching tenant`() {
        // When I create an Agent
        client.load(getResource("acme-billing-agent-v1.yaml")).createOrReplace()
        // ... and a Channel with different tenants
        client.load(getResource("globex-web-channel-v1.yaml")).createOrReplace()

        // Then the Channel is UNRESOLVED, because the tenant is not matching
        val channelResource = assertThatChannelExists("globex-web-stable", ResolveStatus.UNRESOLVED)
        assertThat(channelResource.status.unresolvedRequiredCapabilities).isNotEmpty()

        // And the Channel routing resource should be created
        assertThatChannelRoutingExists("globex-web-stable")
    }

    @Test
    fun `should resolve Channel again if Agent was added later`() {
        // When I add one Agent
        client.load(getResource("acme-billing-agent-v1.yaml")).createOrReplace()
        // ... and a Channel
        client.load(getResource("acme-ivr-channel-v1.yaml")).createOrReplace()

        // Then the Channel is UNRESOLVED, because the required capabilities are not present
        assertThatChannelExists("acme-ivr-stable", ResolveStatus.UNRESOLVED)

        // and the Channel Routing contains the expected capabilities
        var channelRoutingResource = assertThatChannelRoutingExists("acme-ivr-stable")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "view-bill", "1.0.0")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "download-bill", "1.1.0")
        assertThatCapabilityGroupNotExists(channelRoutingResource, "contract-agent")

        // When I add another Agent with the missing capabilities
        client.load(getResource("acme-contract-agent-v1.yaml")).createOrReplace()

        // Then the Channel is RESOLVED, because all required capabilities are present
        assertThatChannelExists("acme-ivr-stable", ResolveStatus.RESOLVED)

        // and the Channel Routing contains the expected capabilities
        channelRoutingResource = assertThatChannelRoutingExists("acme-ivr-stable")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "view-bill", "1.0.0")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "download-bill", "1.1.0")
        assertThatCapabilityExists(channelRoutingResource, "contract-agent", "view-contract", "1.1.0")
    }

    @Test
    fun `should resolve Channel again if Agent was removed`() {
        // When I create two Agents
        client.load(getResource("acme-billing-agent-v1.yaml")).createOrReplace()
        client.load(getResource("acme-contract-agent-v1.yaml")).createOrReplace()
        // ... and a Channel
        client.load(getResource("acme-ivr-channel-v1.yaml")).createOrReplace()

        // Then the Channel is RESOLVED, because all required capabilities are present
        assertThatChannelExists("acme-ivr-stable", ResolveStatus.RESOLVED)

        // and the Channel Routing contains the expected capabilities
        var channelRoutingResource = assertThatChannelRoutingExists("acme-ivr-stable")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "view-bill", "1.0.0")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "download-bill", "1.1.0")
        assertThatCapabilityExists(channelRoutingResource, "contract-agent", "view-contract", "1.1.0")

        // When I remove one Agent
        client.load(getResource("acme-contract-agent-v1.yaml")).delete()

        // Then the Channel is UNRESOLVED, because the required capabilities are not present anymore
        assertThatChannelExists("acme-ivr-stable", ResolveStatus.UNRESOLVED)

        // and the Channel Routing contains the expected capabilities
        channelRoutingResource = assertThatChannelRoutingExists("acme-ivr-stable")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "view-bill", "1.0.0")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "download-bill", "1.1.0")
        assertThatCapabilityGroupNotExists(channelRoutingResource, "contract-agent")
    }

    @Test
    fun `should resolve Channel again if Agent was updated`() {
        // When I create two Agents
        client.load(getResource("acme-billing-agent-v1.yaml")).createOrReplace()
        client.load(getResource("acme-contract-agent-v1.yaml")).createOrReplace()
        // ... and a Channel
        client.load(getResource("acme-ivr-channel-v1.yaml")).createOrReplace()

        // Then the Channel is RESOLVED, because the required capabilities are present
        assertThatChannelExists("acme-ivr-stable", ResolveStatus.RESOLVED)

        // and the Channel Routing contains the expected capabilities
        var channelRoutingResource = assertThatChannelRoutingExists("acme-ivr-stable")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "view-bill", "1.0.0")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "download-bill", "1.1.0")
        assertThatCapabilityExists(channelRoutingResource, "contract-agent", "view-contract", "1.1.0")

        // When I update one Agent that no longer contains the required capability
        client.load(getResource("acme-contract-agent-v2.yaml")).createOrReplace()

        // Then the Channel is UNRESOLVED, because the required capability is not present anymore
        assertThatChannelExists("acme-ivr-stable", ResolveStatus.UNRESOLVED)

        // and the Channel Routing contains the expected capabilities
        channelRoutingResource = assertThatChannelRoutingExists("acme-ivr-stable")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "view-bill", "1.0.0")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "download-bill", "1.1.0")
        assertThatCapabilityGroupNotExists(channelRoutingResource, "contract-agent")
    }

    @Test
    fun `should update Channel Routing capabilities if Agent was updated with new capability version`() {
        // When I create two Agents
        client.load(getResource("acme-billing-agent-v1.yaml")).createOrReplace()
        client.load(getResource("acme-contract-agent-v1.yaml")).createOrReplace()
        // ... and a Channel
        client.load(getResource("acme-ivr-channel-v1.yaml")).createOrReplace()

        // Then the Channel is RESOLVED, because the required capabilities are present
        assertThatChannelExists("acme-ivr-stable", ResolveStatus.RESOLVED)

        // and the Channel Routing contains the capability 'download-bill' in version '1.1.0'
        var channelRoutingResource = assertThatChannelRoutingExists("acme-ivr-stable")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "download-bill", "1.1.0")

        // When I update the Agent 'download-bill' capability to version '1.2.0'
        client.load(getResource("acme-billing-agent-v2.yaml")).createOrReplace()

        // Then the Channel is still RESOLVED
        assertThatChannelExists("acme-ivr-stable", ResolveStatus.RESOLVED)

        // and the Channel Routing contains the updated capability 'download-bill' in version '1.2.0'
        channelRoutingResource = assertThatChannelRoutingExists("acme-ivr-stable")
        assertThatCapabilityExists(channelRoutingResource, "billing-agent-stable", "download-bill", "1.2.0")
    }

    private fun getResource(resourceName: String): FileInputStream {
        return FileInputStream(ResourceUtils.getFile("classpath:$resourceName"))
    }

    private fun assertThatChannelExists(channelName: String, expectedStatus: ResolveStatus) =
        Awaitility.await("ChannelResource '$channelName' with expected status '$expectedStatus' not found.")
            .atMost(5, TimeUnit.SECONDS)
            .pollInterval(50, TimeUnit.MILLISECONDS)
            .until(
                {
                    client.resources(ChannelResource::class.java).list().items
                        .find { it.metadata.name == channelName }
                },
                { resource: ChannelResource? ->
                    resource?.status != null && resource.status.resolveStatus == expectedStatus
                }
            )!!

    private fun assertThatChannelRoutingExists(channelRoutingName: String) =
        Awaitility.await("ChannelRoutingResource '$channelRoutingName' not found.")
            .atMost(5, TimeUnit.SECONDS)
            .pollInterval(50, TimeUnit.MILLISECONDS)
            .until(
                {
                    client.resources(ChannelRoutingResource::class.java).list().items
                        .find { it.metadata.name == channelRoutingName }
                },
                { resource: ChannelRoutingResource? -> resource != null }
            )!!

    private fun assertThatCapabilityExists(
        channelRoutingResource: ChannelRoutingResource,
        agentName: String,
        capabilityName: String,
        capabilityVersion: String
    ) {
        val capabilityGroup = channelRoutingResource.spec?.capabilityGroups?.find { it.name == agentName }
        assertThat(capabilityGroup).isNotNull()
        val capability = capabilityGroup?.capabilities?.find { it.name == capabilityName && it.providedVersion == capabilityVersion }
        assertThat(capability).isNotNull()
    }

    private fun assertThatCapabilityGroupNotExists(channelRoutingResource: ChannelRoutingResource, agentName: String) =
        assertThat(channelRoutingResource.spec?.capabilityGroups?.find { it.name == agentName }).isNull()

}
