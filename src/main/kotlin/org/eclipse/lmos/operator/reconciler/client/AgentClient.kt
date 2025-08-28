/*
 * SPDX-FileCopyrightText: 2025 Deutsche Telekom AG and others
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.lmos.operator.reconciler.client

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class AgentClient(
    webClientBuilder: WebClient.Builder,
    private val objectMapper: ObjectMapper,
) {
    private val webClient = webClientBuilder.build()

    fun <T> get(
        serviceUrl: String,
        clazz: Class<T>,
    ): T {
        val response =
            webClient
                .get()
                .uri(serviceUrl)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String::class.java)
                .block()

        require(!response.isNullOrEmpty()) { "Response ($serviceUrl) is empty!" }

        return objectMapper.readValue(response, clazz)
    }
}
