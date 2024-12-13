/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator

import io.fabric8.kubernetes.client.KubernetesClient
import io.javaoperatorsdk.operator.springboot.starter.test.EnableMockOperator
import org.assertj.core.api.Assertions
import org.eclipse.lmos.operator.resources.ChannelResource
import org.eclipse.lmos.operator.resources.ChannelRoutingResource
import org.eclipse.lmos.operator.server.routing.X_SUBSET_HEADER
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.util.ResourceUtils
import java.io.FileInputStream

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.allow-bean-definition-overriding=true"],
    classes = [OperatorApplication::class],
)
@AutoConfigureWebTestClient
@EnableMockOperator(
    crdPaths = [
        "classpath:META-INF/fabric8/channels.lmos.eclipse-v1.yml",
        "classpath:META-INF/fabric8/agents.lmos.eclipse-v1.yml",
        "classpath:META-INF/fabric8/channelroutings.lmos.eclipse-v1.yml",
        "classpath:META-INF/fabric8/channelrollouts.lmos.eclipse-v1.yml",
    ],
)
class CustomResourcesControllerIntegrationTest {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var client: KubernetesClient

    @Test
    fun shouldReturnChannels() {
        // Given I create two Channel resources
        client.load(getResource("acme-web-channel-v1.yaml")).create()
        client.load(getResource("acme-ivr-channel-v1.yaml")).create()

        // When I call the API
        val result =
            webTestClient
                .get()
                .uri("/apis/v1/tenants/acme/channels")
                .header(X_SUBSET_HEADER, "stable")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ChannelResource::class.java).returnResult()

        val responseBody = result.responseBody

        // Then the two created channel resources should be returned
        Assertions.assertThat(responseBody).hasSize(2)
    }

    @Test
    fun shouldReturnChannelResource() {
        // Given I create an ChannelResource
        client.load(getResource("acme-web-channel-v1.yaml")).createOrReplace()

        // When I call the API
        val result =
            webTestClient
                .get()
                .uri("/apis/v1/tenants/acme/channels/web")
                .header(X_SUBSET_HEADER, "stable")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChannelResource::class.java).returnResult()

        val responseBody = result.responseBody

        // Then the created channel resource should be returned
        Assertions.assertThat(responseBody).isNotNull()
        Assertions.assertThat(responseBody!!.metadata.name).isEqualTo("acme-web-stable")
    }

    @Test
    fun shouldReturnChannelRoutingResource() {
        // Given I create an ChannelRouting
        client.load(getResource("channel-routing.yaml")).createOrReplace()

        // When I call the API
        val result =
            webTestClient
                .get()
                .uri("/apis/v1/tenants/acme/channels/web/routing")
                .header(X_SUBSET_HEADER, "stable")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChannelRoutingResource::class.java).returnResult()

        val responseBody = result.responseBody

        // Then the created channel routing resource should be returned
        Assertions.assertThat(responseBody).isNotNull()
        Assertions.assertThat(responseBody!!.metadata.name).isEqualTo("acme-web-stable")
    }

    @Test
    fun shouldReturnNotFound() {
        // Given there is no channel routing

        // When I call the API the response should be 404 no content

        webTestClient
            .get()
            .uri("/apis/v1/tenants/de/channels/unknown/routing")
            .header(X_SUBSET_HEADER, "stable")
            .exchange()
            .expectStatus().isNotFound()
    }

    private fun getResource(resourceName: String): FileInputStream {
        return FileInputStream(ResourceUtils.getFile("classpath:$resourceName"))
    }
}
