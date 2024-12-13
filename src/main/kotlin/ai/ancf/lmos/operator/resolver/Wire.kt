/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.resolver

import org.eclipse.lmos.operator.resources.AgentResource
import org.eclipse.lmos.operator.resources.ProvidedCapability
import org.eclipse.lmos.operator.resources.RequiredCapability

/**
 * A wire connecting a [AgentResource] to a [RequiredCapability].
 */
data class Wire<P>(
    /**
     * The [RequiredCapability] for this wire.
     */
    val requiredCapability: RequiredCapability,
    /**
     * The [ProvidedCapability] for this wire.
     */
    val providedCapability: ProvidedCapability,
    /**
     * The resource providing the [capability][.getProvidedCapability].
     */
    val provider: P,
    // val provider: AgentResource
)
