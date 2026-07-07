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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** HTTP client for Kintsugi /killbill/tax/estimate. */
public final class KintsugiTaxClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SIGNATURE_HEADER = "X-Killbill-Kintsugi-Signature";
    private static final String API_KEY_HEADER = "X-Killbill-ApiKey";

    private final HttpClient httpClient;
    private final String kintsugiBaseUrl;
    private final String hmacSecret;
    private final String tenantApiKey;

    public KintsugiTaxClient(
            final String kintsugiBaseUrl,
            final String hmacSecret,
            final String tenantApiKey) {
        this.kintsugiBaseUrl = kintsugiBaseUrl.endsWith("/")
                ? kintsugiBaseUrl.substring(0, kintsugiBaseUrl.length() - 1)
                : kintsugiBaseUrl;
        this.hmacSecret = hmacSecret;
        this.tenantApiKey = tenantApiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public List<TaxLineResult> estimate(
            final ObjectNode requestBody,
            final boolean commit) throws Exception {
        final String path = commit ? "/killbill/tax/commit" : "/killbill/tax/estimate";
        final byte[] bodyBytes = MAPPER.writeValueAsBytes(requestBody);
        final String signature = hmacSha256Hex(hmacSecret, bodyBytes);

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(kintsugiBaseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(SIGNATURE_HEADER, signature)
                .header(API_KEY_HEADER, tenantApiKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                .build();

        final HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Kintsugi tax API returned HTTP " + response.statusCode());
        }

        return parseTaxLines(response.body());
    }

    static List<TaxLineResult> parseTaxLines(final String responseJson) throws Exception {
        final List<TaxLineResult> results = new ArrayList<>();
        final JsonNode root = MAPPER.readTree(responseJson);
        final JsonNode documents = root.path("documents");
        if (!documents.isArray()) {
            return results;
        }
        for (final JsonNode doc : documents) {
            final JsonNode lineItems = doc.path("line_items");
            if (!lineItems.isArray()) {
                continue;
            }
            for (final JsonNode line : lineItems) {
                final String externalId = line.path("external_id").asText(null);
                final BigDecimal taxAmount = new BigDecimal(line.path("tax_amount").asText("0"));
                final BigDecimal rate = new BigDecimal(line.path("rate").asText("0"));
                if (externalId != null) {
                    results.add(new TaxLineResult(externalId, taxAmount, rate));
                }
            }
        }
        return results;
    }

    static String hmacSha256Hex(final String secret, final byte[] payload) throws Exception {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        final byte[] digest = mac.doFinal(payload);
        final StringBuilder sb = new StringBuilder(digest.length * 2);
        for (final byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static ObjectNode buildEstimateRequest(
            final String requestId,
            final String currency,
            final String invoiceId,
            final String accountId,
            final boolean dryRun,
            final ArrayNode lineItems,
            final ObjectNode shipTo,
            final ObjectNode billTo,
            final ObjectNode customer,
            final String transactionDate) {
        final ObjectNode root = MAPPER.createObjectNode();
        root.put("id", requestId);
        root.put("currency_code", currency);
        final ObjectNode document = MAPPER.createObjectNode();
        document.put("id", invoiceId);
        document.put("account_id", accountId);
        document.put("dry_run", dryRun);
        document.put("transaction_date", transactionDate);
        document.set("ship_to", shipTo);
        document.set("bill_to", billTo);
        document.set("customer", customer);
        document.set("line_items", lineItems);
        root.set("documents", MAPPER.createArrayNode().add(document));
        return root;
    }

    public static final class TaxLineResult {
        private final String lineExternalId;
        private final BigDecimal taxAmount;
        private final BigDecimal ratePercent;

        public TaxLineResult(
                final String lineExternalId,
                final BigDecimal taxAmount,
                final BigDecimal ratePercent) {
            this.lineExternalId = lineExternalId;
            this.taxAmount = taxAmount;
            this.ratePercent = ratePercent;
        }

        public String lineExternalId() {
            return lineExternalId;
        }

        public BigDecimal taxAmount() {
            return taxAmount;
        }

        public BigDecimal ratePercent() {
            return ratePercent;
        }
    }
}
