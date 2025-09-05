/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.server.routing

import org.eclipse.lmos.operator.DEPLOYMENT_SUBSET_LABEL_DEFAULT_VALUE
import org.eclipse.lmos.operator.resources.ChannelResource
import org.eclipse.lmos.operator.resources.ChannelRoutingResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

const val X_SUBSET_HEADER: String = "x-subset"
const val X_NAMESPACE_HEADER: String = "x-namespace"

private const val KUBERNETES_DEFAULT_NAMESPACE = "default"

@RestController
@RequestMapping("/apis/v1/tenants/{tenant}", produces = [MediaType.APPLICATION_JSON_VALUE])
class CustomResourcesController(
    private val customResourcesService: CustomResourcesService,
) {
    @GetMapping("/channels")
    fun getChannels(
        @RequestHeader(name = X_SUBSET_HEADER) subsetHeader: String = DEPLOYMENT_SUBSET_LABEL_DEFAULT_VALUE,
        @RequestHeader(name = X_NAMESPACE_HEADER, required = false) namespaceHeader: String = KUBERNETES_DEFAULT_NAMESPACE,
        @PathVariable tenant: String,
    ) = customResourcesService.getChannels(tenant, subsetHeader, namespaceHeader)

    @GetMapping("/channels/{channel}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getChannel(
        @RequestHeader(name = X_SUBSET_HEADER) subsetHeader: String = DEPLOYMENT_SUBSET_LABEL_DEFAULT_VALUE,
        @RequestHeader(name = X_NAMESPACE_HEADER, required = false) namespaceHeader: String = KUBERNETES_DEFAULT_NAMESPACE,
        @PathVariable tenant: String,
        @PathVariable channel: String,
    ): ResponseEntity<ChannelResource> =
        customResourcesService.getChannel(tenant, channel, subsetHeader, namespaceHeader)?.let {
            ResponseEntity.ok(it)
        } ?: ResponseEntity.notFound().build()

    @GetMapping("/channels/{channel}/routing", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getRouting(
        @RequestHeader(name = X_SUBSET_HEADER) subsetHeader: String = DEPLOYMENT_SUBSET_LABEL_DEFAULT_VALUE,
        @RequestHeader(name = X_NAMESPACE_HEADER, required = false) namespaceHeader: String = KUBERNETES_DEFAULT_NAMESPACE,
        @PathVariable tenant: String,
        @PathVariable channel: String,
    ): ResponseEntity<ChannelRoutingResource> =
        customResourcesService.getRouting(tenant, channel, subsetHeader, namespaceHeader)?.let {
            ResponseEntity.ok(it)
        } ?: ResponseEntity.notFound().build()
}
