/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator

import org.springframework.boot.test.context.PropertyMapping
import org.springframework.context.annotation.Import

@Retention(AnnotationRetention.RUNTIME)
@Import(
    TestConfig::class,
)
@PropertyMapping("javaoperatorsdk.test")
annotation class EnableMockOperator(
    /**
     * Define a list of files that contain CustomResourceDefinitions for the tested operator. If the
     * file to be loaded is shall be loaded from the classpath prefix it with 'classpath', otherwise
     * provide a path relative to the current working directory.
     *
     * @return List of files
     */
    @get:PropertyMapping("crd-paths") val crdPaths: Array<String> = [],
)
