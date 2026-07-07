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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class InvoiceRequestMapperTest {

    @Test
    void formatAmountUsesTwoDecimalPlaces() {
        assertEquals("100.00", InvoiceRequestMapper.formatAmount(new BigDecimal("100")));
        assertEquals("99.99", InvoiceRequestMapper.formatAmount(new BigDecimal("99.994")));
    }

    @Test
    void externalChargeUsesDefaultProductLabels() {
        assertEquals("Physical", InvoiceRequestMapper.EXTERNAL_CHARGE_CATEGORY);
        assertEquals("General Physical", InvoiceRequestMapper.EXTERNAL_CHARGE_SUBCATEGORY);
    }

    @Test
    void toEstimateRequestMapsInvoiceFields() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID itemId = UUID.randomUUID();

        final Account account = Mockito.mock(Account.class);
        when(account.getId()).thenReturn(accountId);
        when(account.getExternalKey()).thenReturn("acct-ext");
        when(account.getCountry()).thenReturn("US");
        when(account.getPostalCode()).thenReturn("94105");
        when(account.getStateOrProvince()).thenReturn("CA");
        when(account.getCity()).thenReturn("San Francisco");
        when(account.getAddress1()).thenReturn("1 Market St");
        when(account.getEmail()).thenReturn("billing@example.com");
        when(account.getName()).thenReturn("Example Inc");

        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        when(item.getId()).thenReturn(itemId);
        when(item.getAmount()).thenReturn(new BigDecimal("100.00"));
        when(item.getQuantity()).thenReturn(BigDecimal.ONE);
        when(item.getDescription()).thenReturn("Widget");
        when(item.getInvoiceItemType()).thenReturn(InvoiceItemType.EXTERNAL_CHARGE);
        when(item.getPlanName()).thenReturn(null);
        when(item.getPrettyProductName()).thenReturn(null);

        final Invoice invoice = Mockito.mock(Invoice.class);
        when(invoice.getId()).thenReturn(invoiceId);
        when(invoice.getAccountId()).thenReturn(accountId);
        when(invoice.getCurrency()).thenReturn(Currency.USD);
        when(invoice.getInvoiceDate()).thenReturn(new LocalDate(2026, 1, 15));
        when(invoice.getInvoiceNumber()).thenReturn(42);
        when(invoice.getInvoiceItems()).thenReturn(List.of(item));

        final ObjectNode request = InvoiceRequestMapper.toEstimateRequest(
                invoice, account, true, "tenant-1");

        assertEquals("USD", request.path("currency_code").asText());
        assertEquals("killbill-kintsugi", request.path("plugin_name").asText());
        assertEquals("tenant-1", request.path("tenant_id").asText());

        final ObjectNode document = (ObjectNode) request.path("documents").get(0);
        assertTrue(document.path("dry_run").asBoolean());
        assertEquals("2026-01-15", document.path("transaction_date").asText());
        assertEquals(42, document.path("invoice_number").asInt());

        final ObjectNode line = (ObjectNode) document.path("line_items").get(0);
        assertEquals(itemId.toString(), line.path("external_id").asText());
        assertEquals("Physical", line.path("product_category").asText());
        assertEquals("General Physical", line.path("product_subcategory").asText());
    }
}
