/*
 * Copyright 2026 Kintsugi Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.trykintsugi.killbill.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Optional HTTP client for Aviate billing accounts.
 * Absent Aviate plugin, 404, or transport errors return empty — callers fall back to custom fields.
 */
public final class AviateBillingAccountClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AviateBillingAccountClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String API_KEY_HEADER = "X-Killbill-ApiKey";
    private static final String API_SECRET_HEADER = "X-Killbill-ApiSecret";

    private final HttpClient httpClient;

    public AviateBillingAccountClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .version(HttpClient.Version.HTTP_1_1)
                .build());
    }

    AviateBillingAccountClient(final HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Optional<AviateBillingAccount> fetchForKbAccountId(
            final String killbillBaseUrl,
            final UUID kbAccountId,
            final String tenantApiKey,
            final String tenantApiSecret,
            final String aviateIdToken) {
        if (isBlank(killbillBaseUrl)
                || kbAccountId == null
                || isBlank(tenantApiKey)
                || isBlank(tenantApiSecret)
                || isBlank(aviateIdToken)) {
            return Optional.empty();
        }

        final String baseUrl = killbillBaseUrl.endsWith("/")
                ? killbillBaseUrl.substring(0, killbillBaseUrl.length() - 1)
                : killbillBaseUrl;
        final String path = "/plugins/aviate-plugin/v1/ba/forKbAccountId/" + kbAccountId;

        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(3))
                    .header("Authorization", "Bearer " + aviateIdToken.trim())
                    .header(API_KEY_HEADER, tenantApiKey)
                    .header(API_SECRET_HEADER, tenantApiSecret)
                    .GET()
                    .build();

            final HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                LOGGER.debug("No Aviate billing account for KB account {}", kbAccountId);
                return Optional.empty();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn(
                        "Aviate billing account lookup returned HTTP {} for KB account {}",
                        response.statusCode(),
                        kbAccountId);
                return Optional.empty();
            }

            final AviateBillingAccount billingAccount =
                    AviateBillingAccount.fromJson(MAPPER.readTree(response.body()));
            if (billingAccount == null) {
                return Optional.empty();
            }
            return Optional.of(billingAccount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.debug("Aviate billing account lookup interrupted for KB account {}", kbAccountId);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.debug(
                    "Aviate billing account lookup failed for KB account {}: {}",
                    kbAccountId,
                    e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
