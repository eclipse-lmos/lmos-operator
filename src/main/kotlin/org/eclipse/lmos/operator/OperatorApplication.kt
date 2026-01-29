/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator

import org.eclipse.lmos.operator.reconciler.EmbeddingProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication
@Import(OperatorConfig::class)
@EnableConfigurationProperties(EmbeddingProperties::class)
class OperatorApplication

fun main(args: Array<String>) {
    runApplication<OperatorApplication>(*args)
}
