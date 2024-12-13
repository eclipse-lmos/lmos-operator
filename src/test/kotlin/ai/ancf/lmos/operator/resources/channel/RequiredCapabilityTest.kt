/*
 * SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.resources.channel

import org.assertj.core.api.Assertions
import org.eclipse.lmos.operator.data.TestDataGenerator
import org.junit.jupiter.api.Test

class RequiredCapabilityTest {
    @Test
    fun testEqualsAndHasCode() {
        val requiredCapability1 = TestDataGenerator.createRequiredCapability()
        val requiredCapability2 = TestDataGenerator.createRequiredCapability()

        Assertions.assertThat(requiredCapability1 == requiredCapability2).isTrue()
        Assertions.assertThat(requiredCapability1).hasSameHashCodeAs(requiredCapability2)
    }
}
