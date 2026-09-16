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

import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class TestInvoiceTaxIdempotency {

    @Test(groups = "fast")
    public void testReturnsFalseWhenNoTaxItems() {
        final UUID itemId = UUID.randomUUID();
        final InvoiceItem charge = chargeItem(itemId, new BigDecimal("100"));
        final Invoice invoice = invoiceWithItems(charge);

        Assert.assertFalse(InvoiceTaxIdempotency.allTaxableItemsAlreadyTaxed(invoice));
    }

    @Test(groups = "fast")
    public void testReturnsTrueWhenAllTaxableLinesHaveTaxItems() {
        final UUID chargeId = UUID.randomUUID();
        final InvoiceItem charge = chargeItem(chargeId, new BigDecimal("100"));
        final InvoiceItem tax = taxItem(chargeId, new BigDecimal("8.25"));
        final Invoice invoice = invoiceWithItems(charge, tax);

        Assert.assertTrue(InvoiceTaxIdempotency.allTaxableItemsAlreadyTaxed(invoice));
    }

    @Test(groups = "fast")
    public void testReturnsFalseWhenOnlySomeLinesTaxed() {
        final UUID chargeId1 = UUID.randomUUID();
        final UUID chargeId2 = UUID.randomUUID();
        final InvoiceItem charge1 = chargeItem(chargeId1, new BigDecimal("100"));
        final InvoiceItem charge2 = chargeItem(chargeId2, new BigDecimal("50"));
        final InvoiceItem tax = taxItem(chargeId1, new BigDecimal("8.25"));
        final Invoice invoice = invoiceWithItems(charge1, charge2, tax);

        Assert.assertFalse(InvoiceTaxIdempotency.allTaxableItemsAlreadyTaxed(invoice));
    }

    @Test(groups = "fast")
    public void testIgnoresAdjustmentItemsWhenCheckingSalesCoverage() {
        final UUID chargeId = UUID.randomUUID();
        final InvoiceItem charge = chargeItem(chargeId, new BigDecimal("100"));
        final InvoiceItem tax = taxItem(chargeId, new BigDecimal("8.25"));

        final InvoiceItem adjustment = Mockito.mock(InvoiceItem.class);
        Mockito.when(adjustment.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(adjustment.getAmount()).thenReturn(new BigDecimal("-10"));
        Mockito.when(adjustment.getInvoiceItemType()).thenReturn(InvoiceItemType.ITEM_ADJ);

        final Invoice invoice = invoiceWithItems(charge, tax, adjustment);

        Assert.assertTrue(InvoiceTaxIdempotency.allTaxableItemsAlreadyTaxed(invoice));
        Assert.assertEquals(InvoiceTaxIdempotency.untaxedAdjustmentItems(invoice).size(), 1);
        Assert.assertFalse(InvoiceTaxIdempotency.nothingLeftToTax(invoice));
    }

    @Test(groups = "fast")
    public void testUntaxedAdjustmentsEmptyWhenReturnTaxLinkedToAdj() {
        final UUID chargeId = UUID.randomUUID();
        final UUID adjId = UUID.randomUUID();
        final InvoiceItem charge = chargeItem(chargeId, new BigDecimal("100"));
        final InvoiceItem salesTax = taxItem(chargeId, new BigDecimal("8.25"));
        final InvoiceItem adjustment = Mockito.mock(InvoiceItem.class);
        Mockito.when(adjustment.getId()).thenReturn(adjId);
        Mockito.when(adjustment.getAmount()).thenReturn(new BigDecimal("-10"));
        Mockito.when(adjustment.getInvoiceItemType()).thenReturn(InvoiceItemType.ITEM_ADJ);
        final InvoiceItem returnTax = taxItem(adjId, new BigDecimal("-0.83"));
        final Invoice invoice = invoiceWithItems(charge, salesTax, adjustment, returnTax);

        Assert.assertTrue(InvoiceTaxIdempotency.allTaxableItemsAlreadyTaxed(invoice));
        Assert.assertTrue(InvoiceTaxIdempotency.untaxedAdjustmentItems(invoice).isEmpty());
        Assert.assertTrue(InvoiceTaxIdempotency.nothingLeftToTax(invoice));
    }

    @Test(groups = "fast")
    public void testReturnsFalseWhenTaxableLineHasNullId() {
        final InvoiceItem chargeWithoutId = Mockito.mock(InvoiceItem.class);
        Mockito.when(chargeWithoutId.getId()).thenReturn(null);
        Mockito.when(chargeWithoutId.getAmount()).thenReturn(new BigDecimal("100"));
        Mockito.when(chargeWithoutId.getInvoiceItemType()).thenReturn(InvoiceItemType.EXTERNAL_CHARGE);

        final Invoice invoice = invoiceWithItems(chargeWithoutId);

        Assert.assertFalse(InvoiceTaxIdempotency.allTaxableItemsAlreadyTaxed(invoice));
    }

    private static Invoice invoiceWithItems(final InvoiceItem... items) {
        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of(items));
        return invoice;
    }

    private static InvoiceItem chargeItem(final UUID id, final BigDecimal amount) {
        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        Mockito.when(item.getId()).thenReturn(id);
        Mockito.when(item.getAmount()).thenReturn(amount);
        Mockito.when(item.getInvoiceItemType()).thenReturn(InvoiceItemType.EXTERNAL_CHARGE);
        return item;
    }


    @Test(groups = "fast")
    public void testAdjOnlyInvoiceHasNoSalesWorkRemaining() {
        final UUID adjId = UUID.randomUUID();
        final InvoiceItem adjustment = Mockito.mock(InvoiceItem.class);
        Mockito.when(adjustment.getId()).thenReturn(adjId);
        Mockito.when(adjustment.getAmount()).thenReturn(new BigDecimal("-10"));
        Mockito.when(adjustment.getInvoiceItemType()).thenReturn(InvoiceItemType.ITEM_ADJ);
        final Invoice invoice = invoiceWithItems(adjustment);

        Assert.assertTrue(InvoiceTaxIdempotency.allTaxableItemsAlreadyTaxed(invoice));
        Assert.assertEquals(InvoiceTaxIdempotency.untaxedAdjustmentItems(invoice).size(), 1);
        Assert.assertFalse(InvoiceTaxIdempotency.nothingLeftToTax(invoice));
    }

    @Test(groups = "fast")
    public void testCreditOnlyInvoiceHasNoSalesOrReturnWork() {
        final InvoiceItem credit = Mockito.mock(InvoiceItem.class);
        Mockito.when(credit.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(credit.getAmount()).thenReturn(new BigDecimal("-5"));
        Mockito.when(credit.getInvoiceItemType()).thenReturn(InvoiceItemType.CREDIT_ADJ);
        final Invoice invoice = invoiceWithItems(credit);

        Assert.assertTrue(InvoiceTaxIdempotency.allTaxableItemsAlreadyTaxed(invoice));
        Assert.assertTrue(InvoiceTaxIdempotency.untaxedAdjustmentItems(invoice).isEmpty());
        Assert.assertTrue(InvoiceTaxIdempotency.nothingLeftToTax(invoice));
    }

    private static InvoiceItem taxItem(final UUID linkedId, final BigDecimal amount) {
        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        Mockito.when(item.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(item.getLinkedItemId()).thenReturn(linkedId);
        Mockito.when(item.getAmount()).thenReturn(amount);
        Mockito.when(item.getInvoiceItemType()).thenReturn(InvoiceItemType.TAX);
        return item;
    }
}
