/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.alert

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.eclipse.lmos.operator.config.AlertConfig
import org.eclipse.lmos.operator.resources.RequiredCapability
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.HttpURLConnection
import java.net.URL

@Component
class AlertClient(
    private val alertConfig: AlertConfig,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    fun sendUnresolvedChannelAlert(
        namespace: String,
        channelName: String,
        unresolved: Set<RequiredCapability>,
        reason: String,
    ) {
        val payload = mapOf(
            "namespace" to namespace,
            "channel" to channelName,
            "unresolvedCapabilities" to unresolved.map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "version" to it.version,
                )
            },
            "reason" to reason,
        )

        // Always emit a structured log for local monitoring
        log.warn(
            "ALERT: Channel unresolved - namespace={} channel={} unresolved={} reason={}",
            namespace,
            channelName,
            unresolved,
            reason,
        )

        val webhookUrl = alertConfig.webhookUrl

        if (webhookUrl.isNullOrBlank()) {
            log.debug("No alert webhook configured (lmos.alert.webhook-url). Skipping webhook delivery.")
            return
        }

        try {
            val url = URL(webhookUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val body = objectMapper.writeValueAsBytes(payload)
            conn.outputStream.use { it.write(body) }

            val code = conn.responseCode
            if (code in 200..299) {
                log.info("Alert delivered to webhook: {} (status={})", webhookUrl, code)
            } else {
                log.error("Failed to deliver alert to webhook: {} (status={})", webhookUrl, code)
            }
        } catch (ex: Exception) {
            log.error("Exception while sending alert to webhook: {}", ex.message, ex)
        }
    }
}
