/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler

const val LABEL_SELECTOR = "lmos-agent=true"

const val WELL_KNOWN_AGENT_SPEC_ENDPOINT = ".well-known/capabilities.json"

const val ERROR_RETRY_INITIAL_INTERVAL_MS = 5000L
const val ERROR_RETRY_INTERVAL_MULTIPLIER = 1.5
const val ERROR_RETRY_MAX_ATTEMPTS = 3
