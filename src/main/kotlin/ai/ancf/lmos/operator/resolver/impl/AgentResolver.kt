/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package ai.ancf.lmos.operator.resolver.impl

import ai.ancf.lmos.operator.resolver.ResolveContext
import ai.ancf.lmos.operator.resolver.Resolver
import ai.ancf.lmos.operator.resolver.ResolverException
import ai.ancf.lmos.operator.resolver.Wire
import ai.ancf.lmos.operator.resources.AgentResource
import ai.ancf.lmos.operator.resources.RequiredCapability
import java.util.function.Consumer

class AgentResolver : Resolver<AgentResource> {
    @Throws(ResolverException::class)
    override fun resolve(
        requiredCapabilities: Set<RequiredCapability>,
        resolveContext: ResolveContext<AgentResource>,
    ): Set<Wire<AgentResource>> {
        val agentResources = resolveContext.availableResources

        if (agentResources.isEmpty()) {
            throw ResolverException("Resolve context is empty", requiredCapabilities)
        }

        val bestMatchingCapabilities = mutableSetOf<Wire<AgentResource>>()
        val unresolvedRequiredCapabilities = mutableSetOf<RequiredCapability>()
        requiredCapabilities.forEach(
            Consumer { requiredCapability: RequiredCapability ->
                val wireCapabilities = resolveContext.findCapabilityProviders(requiredCapability)
                if (wireCapabilities.isEmpty()) {
                    unresolvedRequiredCapabilities.add(requiredCapability)
                } else {
                    val strategy = requiredCapability.strategy
                    val bestMatchingWire =
                        CapabilityResolver.findBestMatchingWire(
                            wireCapabilities,
                            strategy,
                        )
                    if (bestMatchingWire != null) {
                        bestMatchingCapabilities.add(bestMatchingWire)
                    } else {
                        unresolvedRequiredCapabilities.add(requiredCapability)
                    }
                }
            },
        )
        if (unresolvedRequiredCapabilities.isNotEmpty()) {
            throw ResolverException("Required capabilities not resolved", unresolvedRequiredCapabilities)
        }

        return bestMatchingCapabilities
    }
}
