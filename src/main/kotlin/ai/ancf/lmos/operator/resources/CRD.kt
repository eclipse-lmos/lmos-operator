/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.resources

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.fabric8.crd.generator.annotation.PrinterColumn
import io.fabric8.generator.annotation.Required
import io.fabric8.kubernetes.api.model.Namespaced
import io.fabric8.kubernetes.client.CustomResource
import io.fabric8.kubernetes.model.annotation.Group
import io.fabric8.kubernetes.model.annotation.Kind
import io.fabric8.kubernetes.model.annotation.Plural
import io.fabric8.kubernetes.model.annotation.ShortNames
import io.fabric8.kubernetes.model.annotation.Singular
import io.fabric8.kubernetes.model.annotation.Version
import org.eclipse.lmos.operator.resolver.ResolveStrategy
import org.eclipse.lmos.operator.resolver.Wire

@Group("lmos.eclipse")
@Version("v1")
@Plural("agents")
@Singular("agent")
@Kind("Agent")
@ShortNames("ag")
class AgentResource : CustomResource<AgentSpec, Void>(), Namespaced

data class AgentSpec(
    var supportedTenants: Set<String> = emptySet(),
    var supportedChannels: Set<String> = emptySet(),
    var providedCapabilities: Set<ProvidedCapability> = emptySet(),
    var description: String = "",
)

sealed class Capability {
    abstract var name: String
    abstract var version: String
}

data class ProvidedCapability(
    @JsonPropertyDescription("The name of the capability")
    @Required
    override var name: String,
    @Required
    override var version: String,
    var description: String = "",
) : Capability()

@Group("lmos.eclipse")
@Version("v1")
@Plural("channels")
@Singular("channel")
@Kind("Channel")
@ShortNames("ch")
class ChannelResource : CustomResource<ChannelSpec, ChannelStatus>(), Namespaced

enum class ResolveStatus {
    RESOLVED,
    UNRESOLVED,
}

data class RequiredCapability(
    @JsonPropertyDescription("The name of the capability")
    @Required
    override var name: String,
    @Required
    override var version: String,
    var strategy: ResolveStrategy = ResolveStrategy.HIGHEST,
) : Capability()

data class ChannelStatus(
    @PrinterColumn(name = "RESOLVE_STATUS")
    var resolveStatus: ResolveStatus = ResolveStatus.UNRESOLVED,
    var unresolvedRequiredCapabilities: Set<RequiredCapability> = emptySet(),
)

@JsonDeserialize
data class ChannelSpec(
    @Required
    var requiredCapabilities: Set<RequiredCapability>,
)

@Group("lmos.eclipse")
@Version("v1")
@Plural("channelrollouts")
@Singular("channelrollout")
@Kind("ChannelRollout")
@ShortNames("crl")
class ChannelRolloutResource : CustomResource<ChannelRolloutSpec, Void>(), Namespaced

data class StableChannel(
    var name: String,
    var weight: Int = 0,
)

data class CanaryChannel(
    var name: String,
    var weight: Int = 0,
)

data class Canary(
    var canaryChannel: CanaryChannel,
    var stableChannel: StableChannel,
)

data class Strategy(
    var canary: Canary,
)

data class ChannelRolloutSpec(
    var strategy: Strategy,
)

@Group("lmos.eclipse")
@Version("v1")
@Plural("channelroutings")
@Singular("channelrouting")
@Kind("ChannelRouting")
@ShortNames("cr")
class ChannelRoutingResource : CustomResource<ChannelRoutingSpec, Void>(), Namespaced

data class ChannelRoutingSpec(
    var capabilityGroups: Set<CapabilityGroup> = setOf(),
)

data class ChannelRoutingStatus(
    @PrinterColumn(name = "RESOLVE_STATUS")
    var resolveStatus: ResolveStatus = ResolveStatus.UNRESOLVED,
    var unresolvedRequiredCapabilities: List<RequiredCapability> = emptyList(),
)

data class CapabilityGroup(
    @Required
    var name: String,
    @Required
    var capabilities: Set<ChannelRoutingCapability>,
    var description: String = "",
)

data class ChannelRoutingCapability(
    @Required
    var name: String,
    @Required
    var requiredVersion: String,
    @Required
    var providedVersion: String,
    @Required
    var host: String,
    var subset: String? = null,
    var description: String = "",
) {
    constructor(wire: Wire<AgentResource>, subset: String?) : this(
        name = wire.providedCapability.name,
        requiredVersion = wire.requiredCapability.version,
        providedVersion = wire.providedCapability.version,
        description = wire.providedCapability.description,
        host = "${wire.provider.metadata.name}.${wire.provider.metadata.namespace}.svc.cluster.local",
        subset = subset,
    )
}

object Labels {
    const val CHANNEL: String = "channel"
    const val TENANT: String = "tenant"
    const val VERSION: String = "version"
    const val SUBSET: String = "subset"
}
