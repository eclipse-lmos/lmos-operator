/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.resolver

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
    private val unresolvedRequiredCapabilities: Set<RequiredCapability>

    /**
     * Create a `ResolutionException` with the specified message, cause
     * and unresolved requirements.
     *
     * @param message The message.
     * @param cause The cause of this exception.
     * @param unresolvedRequiredCapabilities The unresolved required capabilities from
     * mandatory resources or `null` if no unresolved requirements
     * information is provided.
     */
    constructor(message: String, cause: Throwable, unresolvedRequiredCapabilities: Set<RequiredCapability>) : super(
        message,
        cause,
    ) {
        this.unresolvedRequiredCapabilities = Collections.unmodifiableSet(unresolvedRequiredCapabilities)
    }

    /**
     * Create a `ResolutionException` with the specified message.
     *
     * @param message The message.
     */
    constructor(message: String?, unresolvedRequiredCapabilities: Set<RequiredCapability>) : super(message) {
        this.unresolvedRequiredCapabilities = unresolvedRequiredCapabilities
    }

    /**
     * Create a `ResolutionException` with the specified cause.
     *
     * @param cause The cause of this exception.
     */
    constructor(cause: Throwable?) : super(cause) {
        this.unresolvedRequiredCapabilities = emptySet()
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
}
