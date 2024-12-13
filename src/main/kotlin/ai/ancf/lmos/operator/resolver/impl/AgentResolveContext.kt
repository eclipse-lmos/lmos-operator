/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.resolver.impl

import org.eclipse.lmos.operator.resolver.ResolveContext
import org.eclipse.lmos.operator.resolver.Wire
import org.eclipse.lmos.operator.resources.AgentResource
import org.eclipse.lmos.operator.resources.ProvidedCapability
import org.eclipse.lmos.operator.resources.RequiredCapability
import org.semver4j.Semver

class AgentResolveContext(private val agentResources: List<AgentResource>) : ResolveContext<AgentResource> {
    override val availableResources: Collection<AgentResource>
        get() = agentResources

    /**
     * Finds the capability providers that can fulfill the given required capability.
     *
     * @param requiredCapability the required capability to be matched against provided capabilities
     * @return a set of [&lt;AgentResource&gt;][Wire] objects representing the connections between the required capability and the provided capabilities that meet the requirements
     */
    override fun findCapabilityProviders(requiredCapability: RequiredCapability): Set<Wire<AgentResource>> {
        val wiredCapabilities = mutableSetOf<Wire<AgentResource>>()

        for (agentResource in agentResources) {
            val providedCapabilities = agentResource.spec?.providedCapabilities
            if (providedCapabilities != null) {
                for (providedCapability in providedCapabilities) {
                    if (isCapabilityNameEqual(requiredCapability, providedCapability)) {
                        if (isProvidedCapabilityVersionGreaterThanOrEqualTo(
                                providedCapability,
                                requiredCapability.version,
                            )
                        ) {
                            wiredCapabilities.add(
                                Wire(
                                    requiredCapability,
                                    providedCapability,
                                    agentResource,
                                ),
                            )
                        }
                    }
                }
            }
        }
        return wiredCapabilities
    }

    /**
     * Checks if the provided capability's version is greater than or equal to the required capability's version.
     *
     * @param providedCapability the provided capability
     * @param requiredCapabilityVersion the required capability version
     * @return true if the provided capability's version is greater than or equal to the required capability's version, false otherwise
     */
    private fun isProvidedCapabilityVersionGreaterThanOrEqualTo(
        providedCapability: ProvidedCapability,
        requiredCapabilityVersion: String,
    ): Boolean {
        return Semver(providedCapability.version).satisfies(requiredCapabilityVersion)
    }

    /**
     * Checks if the names of the required capability and provided capability are equal.
     *
     * @param requiredCapability the required capability
     * @param providedCapability the provided capability
     * @return true if the names are equal, false otherwise
     */
    private fun isCapabilityNameEqual(
        requiredCapability: RequiredCapability,
        providedCapability: ProvidedCapability,
    ): Boolean {
        return providedCapability.name == requiredCapability.name
    }
}
