/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.resolver

import org.assertj.core.api.Assertions
import org.eclipse.lmos.operator.data.TestDataGenerator
import org.junit.jupiter.api.Test

class AgentWireTest {
    @Test
    fun testEqualsAndHasCode() {
        val wire1 = TestDataGenerator.createAgentWire()
        val wire2 = TestDataGenerator.createAgentWire()

        Assertions.assertThat(wire1 == wire2).isTrue()
        Assertions.assertThat(wire1).hasSameHashCodeAs(wire2)
    }
}
