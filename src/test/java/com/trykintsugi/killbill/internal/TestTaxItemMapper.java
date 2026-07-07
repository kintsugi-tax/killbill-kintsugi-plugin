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
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TestTaxItemMapper {

    @Test(groups = "fast")
    public void testToTaxItemsSkipsZeroTaxLines() {
        final UUID invoiceId = UUID.randomUUID();
        final UUID itemId = UUID.randomUUID();

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getId()).thenReturn(invoiceId);
        Mockito.when(invoice.getInvoiceDate()).thenReturn(new LocalDate(2026, 1, 15));

        final InvoiceItem taxableItem = Mockito.mock(InvoiceItem.class);
        Mockito.when(taxableItem.getId()).thenReturn(itemId);
        Mockito.when(taxableItem.getInvoiceItemType()).thenReturn(InvoiceItemType.EXTERNAL_CHARGE);
        Mockito.when(taxableItem.getAmount()).thenReturn(new BigDecimal("100.00"));
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of(taxableItem));

        final List<KintsugiTaxClient.TaxLineResult> taxLines = List.of(
                new KintsugiTaxClient.TaxLineResult(itemId.toString(), BigDecimal.ZERO, BigDecimal.ZERO),
                new KintsugiTaxClient.TaxLineResult(
                        itemId.toString(), new BigDecimal("8.25"), new BigDecimal("8.25")));

        final Map<UUID, InvoiceItem> taxableItems = TaxItemMapper.indexTaxableItems(invoice);
        final List<InvoiceItem> taxItems = TaxItemMapper.toTaxItems(invoice, taxLines, taxableItems);

        Assert.assertEquals(taxItems.size(), 1);
        Assert.assertEquals(taxItems.get(0).getInvoiceItemType(), InvoiceItemType.TAX);
        Assert.assertEquals(taxItems.get(0).getAmount(), new BigDecimal("8.25"));
        Assert.assertTrue(taxItems.get(0).getDescription().contains("8.25"));
    }

    @Test(groups = "fast")
    public void testToTaxItemsSkipsUnmatchedExternalIds() {
        final UUID invoiceId = UUID.randomUUID();
        final UUID itemId = UUID.randomUUID();

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getId()).thenReturn(invoiceId);
        Mockito.when(invoice.getInvoiceDate()).thenReturn(new LocalDate(2026, 1, 15));

        final InvoiceItem taxableItem = Mockito.mock(InvoiceItem.class);
        Mockito.when(taxableItem.getId()).thenReturn(itemId);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of(taxableItem));

        final List<KintsugiTaxClient.TaxLineResult> taxLines = List.of(
                new KintsugiTaxClient.TaxLineResult(
                        UUID.randomUUID().toString(), new BigDecimal("8.25"), new BigDecimal("8.25")),
                new KintsugiTaxClient.TaxLineResult("not-a-uuid", new BigDecimal("1.00"), new BigDecimal("1.00")));

        final List<InvoiceItem> taxItems = TaxItemMapper.toTaxItems(
                invoice, taxLines, TaxItemMapper.indexTaxableItems(invoice));

        Assert.assertTrue(taxItems.isEmpty());
    }

    @Test(groups = "fast")
    public void testIndexTaxableItemsIgnoresNullIds() {
        final Invoice invoice = Mockito.mock(Invoice.class);
        final InvoiceItem withId = Mockito.mock(InvoiceItem.class);
        final InvoiceItem withoutId = Mockito.mock(InvoiceItem.class);
        final UUID itemId = UUID.randomUUID();

        Mockito.when(withId.getId()).thenReturn(itemId);
        Mockito.when(withoutId.getId()).thenReturn(null);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of(withId, withoutId));

        final Map<UUID, InvoiceItem> indexed = TaxItemMapper.indexTaxableItems(invoice);

        Assert.assertEquals(indexed.size(), 1);
        Assert.assertSame(indexed.get(itemId), withId);
    }
}
