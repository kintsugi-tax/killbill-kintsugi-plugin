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

import org.joda.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class TaxItemMapperTest {

    @Test
    void toTaxItemsSkipsZeroTaxLines() {
        final UUID invoiceId = UUID.randomUUID();
        final UUID itemId = UUID.randomUUID();

        final Invoice invoice = Mockito.mock(Invoice.class);
        when(invoice.getId()).thenReturn(invoiceId);
        when(invoice.getInvoiceDate()).thenReturn(new LocalDate(2026, 1, 15));

        final InvoiceItem taxableItem = Mockito.mock(InvoiceItem.class);
        when(taxableItem.getId()).thenReturn(itemId);
        when(taxableItem.getInvoiceItemType()).thenReturn(InvoiceItemType.EXTERNAL_CHARGE);
        when(taxableItem.getAmount()).thenReturn(new BigDecimal("100.00"));
        when(invoice.getInvoiceItems()).thenReturn(List.of(taxableItem));

        final List<KintsugiTaxClient.TaxLineResult> taxLines = List.of(
                new KintsugiTaxClient.TaxLineResult(itemId.toString(), BigDecimal.ZERO, BigDecimal.ZERO),
                new KintsugiTaxClient.TaxLineResult(itemId.toString(), new BigDecimal("8.25"), new BigDecimal("8.25")));

        final Map<UUID, InvoiceItem> taxableItems = TaxItemMapper.indexTaxableItems(invoice);
        final List<InvoiceItem> taxItems = TaxItemMapper.toTaxItems(invoice, taxLines, taxableItems);

        assertEquals(1, taxItems.size());
        assertEquals(InvoiceItemType.TAX, taxItems.get(0).getInvoiceItemType());
        assertEquals(new BigDecimal("8.25"), taxItems.get(0).getAmount());
        assertTrue(taxItems.get(0).getDescription().contains("8.25"));
    }
}
