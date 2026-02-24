/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.resources

import io.fabric8.kubernetes.api.model.LabelSelector
import io.fabric8.kubernetes.api.model.Namespaced
import io.fabric8.kubernetes.api.model.PodTemplateSpec
import io.fabric8.kubernetes.client.CustomResource
import io.fabric8.kubernetes.model.annotation.Group
import io.fabric8.kubernetes.model.annotation.Kind
import io.fabric8.kubernetes.model.annotation.Plural
import io.fabric8.kubernetes.model.annotation.Singular
import io.fabric8.kubernetes.model.annotation.Version

@Group("argoproj.io")
@Version("v1alpha1")
@Plural("rollouts")
@Singular("rollout")
@Kind("Rollout")
class RolloutResource :
    CustomResource<RolloutSpec, RolloutStatus>(),
    Namespaced

data class RolloutSpec(
    var replicas: Int? = null,
    var selector: LabelSelector? = null,
    var template: PodTemplateSpec? = null,
)

data class RolloutStatus(
    var replicas: Int? = null,
    var availableReplicas: Int? = null,
    var readyReplicas: Int? = null,
    var updatedReplicas: Int? = null,
    var phase: String? = null,
)
