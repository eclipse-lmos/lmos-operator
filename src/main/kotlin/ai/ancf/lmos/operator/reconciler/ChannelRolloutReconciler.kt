/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler

import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.Reconciler
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl
import org.eclipse.lmos.operator.resources.ChannelRolloutResource
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ChannelRolloutReconciler : Reconciler<ChannelRolloutResource> {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    override fun reconcile(
        channelRolloutResource: ChannelRolloutResource,
        context: Context<ChannelRolloutResource>,
    ): UpdateControl<ChannelRolloutResource> {
        log.debug("ChannelRollout reconcile")
        return UpdateControl.noUpdate()
    }
}
