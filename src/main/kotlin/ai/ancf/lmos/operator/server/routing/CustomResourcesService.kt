/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.server.routing

import org.eclipse.lmos.operator.resources.ChannelResource
import org.eclipse.lmos.operator.resources.ChannelRoutingResource

interface CustomResourcesService {
    fun getRouting(
        tenant: String,
        channel: String,
        subset: String,
        namespace: String,
    ): ChannelRoutingResource?

    fun getChannels(
        tenant: String,
        subset: String,
        namespace: String,
    ): List<ChannelResource>

    fun getChannel(
        tenant: String,
        channel: String,
        subset: String,
        namespace: String,
    ): ChannelResource?
}
