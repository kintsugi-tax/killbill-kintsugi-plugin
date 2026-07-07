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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KintsugiTaxClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildEstimateRequestIncludesDocumentFields() throws Exception {
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
        assertEquals("inv-1", document.path("id").asText());
        assertEquals("acc-1", document.path("account_id").asText());
        assertEquals(true, document.path("dry_run").asBoolean());
        assertEquals("acc-1", document.path("customer").path("external_id").asText());
    }

    @Test
    void parseTaxLinesMapsResponse() throws Exception {
        final String json = "{\"documents\":[{\"line_items\":["
                + "{\"external_id\":\"line-1\",\"tax_amount\":\"8.25\",\"rate\":\"8.25\"}"
                + "]}]}";
        final List<KintsugiTaxClient.TaxLineResult> lines = KintsugiTaxClient.parseTaxLines(json);
        assertEquals(1, lines.size());
        assertEquals("line-1", lines.get(0).lineExternalId());
        assertEquals(new BigDecimal("8.25"), lines.get(0).taxAmount());
    }

    @Test
    void hmacIsDeterministic() throws Exception {
        final byte[] body = "{\"id\":\"x\"}".getBytes();
        final String sig1 = KintsugiTaxClient.hmacSha256Hex("secret", body);
        final String sig2 = KintsugiTaxClient.hmacSha256Hex("secret", body);
        assertEquals(sig1, sig2);
        assertFalse(sig1.isEmpty());
    }
}
