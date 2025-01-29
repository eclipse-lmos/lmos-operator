/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.resolver

import org.eclipse.lmos.operator.resources.AgentResource
import org.eclipse.lmos.operator.resources.RequiredCapability
import java.util.Collections

/**
 * Indicates failure to resolve a set of required capabilities.
 *
 * If a resolution failure is caused by a missing mandatory dependency a
 * resolver may include any requirements it has considered in the resolution
 * exception. Clients may access this set of dependencies via the
 * [.getUnresolvedRequiredCapabilities] method.
 */
class ResolverException : Exception {
    private val resolvedWiredCapabilities: Set<Wire<AgentResource>>
    private val unresolvedRequiredCapabilities: Set<RequiredCapability>

    /**
     * Create a [ResolverException] with the wired and unresolved capabilities.
     *
     * @param resolvedWiredCapabilities The resolved wired capabilities.
     * @param unresolvedRequiredCapabilities The unresolved required capabilities.
     */
    constructor(resolvedWiredCapabilities: Set<Wire<AgentResource>>, unresolvedRequiredCapabilities: Set<RequiredCapability>) : super(
        "Required capabilities not resolved: ${unresolvedRequiredCapabilities.joinToString(", ") { it.toString() }}"
    ) {
        this.resolvedWiredCapabilities = resolvedWiredCapabilities
        this.unresolvedRequiredCapabilities = Collections.unmodifiableSet(unresolvedRequiredCapabilities)
    }

    /**
     * Create a [ResolverException] with the specified cause.
     *
     * @param cause The cause of this exception.
     */
    constructor(cause: Throwable?) : super(cause) {
        this.resolvedWiredCapabilities = emptySet()
        this.unresolvedRequiredCapabilities = emptySet()
    }

    /**
     * Create a [ResolverException] with the specified message, cause, wired and unresolved capabilities.
     *
     * @param message The message.
     * @param cause The cause of this exception.
     * @param resolvedWiredCapabilities The resolved wired capabilities.
     * @param unresolvedRequiredCapabilities The unresolved required capabilities.
     */
    constructor(message: String, cause: Throwable, resolvedWiredCapabilities: Set<Wire<AgentResource>>, unresolvedRequiredCapabilities: Set<RequiredCapability>) : super(
        message,
        cause
    ) {
        this.resolvedWiredCapabilities = resolvedWiredCapabilities
        this.unresolvedRequiredCapabilities = Collections.unmodifiableSet(unresolvedRequiredCapabilities)
    }

    /**
     * Return the unresolved required capabilities, if any, for this exception.
     *
     * @return A collection of the unresolved required capabilities for this exception.
     * The returned collection may be empty if no unresolved
     * requirements information is available.
     */
    fun getUnresolvedRequiredCapabilities(): Set<RequiredCapability> {
        return unresolvedRequiredCapabilities
    }

    /**
     * Return the resolved wired capabilities, if any, for this exception.
     *
     * @return A collection of the resolved wired capabilities for this exception.
     * The returned collection may be empty if no wired capabilities are available.
     */
    fun getResolvedWireCapabilities(): Set<Wire<AgentResource>> {
        return resolvedWiredCapabilities
    }
}
