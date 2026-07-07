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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public class TestKintsugiTaxClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WireMockServer wireMockServer;

    @BeforeMethod(groups = "fast")
    public void setUpWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterMethod(groups = "fast")
    public void tearDownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test(groups = "fast")
    public void testBuildEstimateRequestIncludesDocumentFields() throws Exception {
        final ArrayNode lineItems = MAPPER.createArrayNode();
        final ObjectNode line = MAPPER.createObjectNode();
        line.put("external_id", "line-1");
        line.put("amount", "100.00");
        lineItems.add(line);
        final ObjectNode shipTo = MAPPER.createObjectNode();
        shipTo.put("country", "US");

        final ObjectNode billTo = MAPPER.createObjectNode();
        billTo.put("country", "US");
        final ObjectNode customer = MAPPER.createObjectNode();
        customer.put("external_id", "acc-1");

        final ObjectNode root = KintsugiTaxClient.buildEstimateRequest(
                "req-1", "USD", "inv-1", "acc-1", true, lineItems, shipTo, billTo, customer,
                "2026-01-15T00:00:00.000Z");
        final ObjectNode document = (ObjectNode) root.path("documents").get(0);
        Assert.assertEquals(document.path("id").asText(), "inv-1");
        Assert.assertEquals(document.path("account_id").asText(), "acc-1");
        Assert.assertTrue(document.path("dry_run").asBoolean());
        Assert.assertEquals(document.path("customer").path("external_id").asText(), "acc-1");
    }

    @Test(groups = "fast")
    public void testParseTaxLinesMapsResponse() throws Exception {
        final String json = "{\"documents\":[{\"line_items\":["
                + "{\"external_id\":\"line-1\",\"tax_amount\":\"8.25\",\"rate\":\"8.25\"}"
                + "]}]}";
        final List<KintsugiTaxClient.TaxLineResult> lines = KintsugiTaxClient.parseTaxLines(json);
        Assert.assertEquals(lines.size(), 1);
        Assert.assertEquals(lines.get(0).lineExternalId(), "line-1");
        Assert.assertEquals(lines.get(0).taxAmount(), new BigDecimal("8.25"));
    }

    @Test(groups = "fast")
    public void testParseTaxLinesReturnsEmptyForMissingDocuments() throws Exception {
        Assert.assertTrue(KintsugiTaxClient.parseTaxLines("{}").isEmpty());
        Assert.assertTrue(KintsugiTaxClient.parseTaxLines("{\"documents\":null}").isEmpty());
    }

    @Test(groups = "fast")
    public void testHmacIsDeterministic() throws Exception {
        final byte[] body = "{\"id\":\"x\"}".getBytes();
        final String sig1 = KintsugiTaxClient.hmacSha256Hex("secret", body);
        final String sig2 = KintsugiTaxClient.hmacSha256Hex("secret", body);
        Assert.assertEquals(sig1, sig2);
        Assert.assertFalse(sig1.isEmpty());
    }

    @Test(groups = "fast")
    public void testEstimateSendsSignedRequest() throws Exception {
        final String responseBody = "{\"documents\":[{\"line_items\":["
                + "{\"external_id\":\"line-1\",\"tax_amount\":\"1.00\",\"rate\":\"8.25\"}"
                + "]}]}";
        wireMockServer.stubFor(post(urlPathEqualTo("/killbill/tax/estimate"))
                .willReturn(aResponse().withStatus(200).withBody(responseBody)));

        final ObjectNode requestBody = MAPPER.createObjectNode();
        requestBody.put("id", "req-1");
        final byte[] bodyBytes = MAPPER.writeValueAsBytes(requestBody);

        final KintsugiTaxClient client = newClient();
        final List<KintsugiTaxClient.TaxLineResult> lines = client.estimate(requestBody, false);

        Assert.assertEquals(lines.size(), 1);
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/killbill/tax/estimate"))
                .withHeader("X-Killbill-ApiKey", equalTo("tenant-key"))
                .withHeader(
                        "X-Killbill-Kintsugi-Signature",
                        equalTo(KintsugiTaxClient.hmacSha256Hex("hmac-secret", bodyBytes))));
    }

    @Test(groups = "fast")
    public void testCommitUsesCommitPath() throws Exception {
        wireMockServer.stubFor(post(urlPathEqualTo("/killbill/tax/commit"))
                .willReturn(aResponse().withStatus(200).withBody("{\"documents\":[]}")));

        final ObjectNode requestBody = MAPPER.createObjectNode();
        requestBody.put("id", "req-2");

        newClient().estimate(requestBody, true);

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/killbill/tax/commit")));
    }

    @Test(groups = "fast", expectedExceptions = IllegalStateException.class)
    public void testEstimateThrowsOnHttpError() throws Exception {
        wireMockServer.stubFor(post(urlPathEqualTo("/killbill/tax/estimate"))
                .willReturn(aResponse().withStatus(401).withBody("{\"error\":\"unauthorized\"}")));

        newClient().estimate(MAPPER.createObjectNode(), false);
    }

    @Test(groups = "fast")
    public void testStripsTrailingSlashFromBaseUrl() throws Exception {
        wireMockServer.stubFor(post(urlPathEqualTo("/killbill/tax/estimate"))
                .willReturn(aResponse().withStatus(200).withBody("{\"documents\":[]}")));

        final KintsugiTaxClient client = new KintsugiTaxClient(
                wireMockBaseUrl() + "/",
                "hmac-secret",
                "tenant-key");
        client.estimate(MAPPER.createObjectNode(), false);

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/killbill/tax/estimate")));
    }

    private KintsugiTaxClient newClient() {
        return new KintsugiTaxClient(wireMockBaseUrl(), "hmac-secret", "tenant-key");
    }

    private String wireMockBaseUrl() {
        return "http://localhost:" + wireMockServer.port();
    }
}
