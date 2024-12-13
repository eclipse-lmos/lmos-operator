/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.resolver

import org.eclipse.lmos.operator.resources.AgentResource
import org.eclipse.lmos.operator.resources.RequiredCapability

interface Resolver<P> {
    @Throws(ResolverException::class)
    fun resolve(
        requiredCapabilities: Set<RequiredCapability>,
        resolveContext: ResolveContext<P>,
    ): Set<Wire<AgentResource>>
}

interface ResolveContext<P> {
    /**
     * Return the resources that are part of this resolve context.
     *
     * @return A collection of the resources are part for this
     * resolve context. May be empty if there are no available
     * resources. The returned collection may be unmodifiable.
     */
    val availableResources: Collection<P>

    /**
     * Find resources that provide the given required capability.
     *
     * @param requiredCapability The required capability that a resolver is attempting to satisfy.
     *
     * @return A list of Capability objects that match the specified requirement.
     */
    fun findCapabilityProviders(requiredCapability: RequiredCapability): Set<Wire<P>>
}

enum class ResolveStrategy {
    HIGHEST,
    MOST_SIMILAR,
}
