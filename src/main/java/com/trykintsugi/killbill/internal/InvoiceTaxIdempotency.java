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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Detects when tax items were already generated for an invoice (re-invoice idempotency).
 *
 * <p>Sales coverage: every taxable line has a linked {@code TAX} item.
 * Return coverage: every {@code ITEM_ADJ}/{@code REPAIR_ADJ} has a linked {@code TAX}
 * (negative return tax), matching AvaTax's adj-aware idempotency model.
 */
public final class InvoiceTaxIdempotency {

    private InvoiceTaxIdempotency() {}

    /**
     * Returns true when every taxable line on the invoice already has a linked {@code TAX} item.
     * Adjustment lines are ignored for sales coverage (see {@link #untaxedAdjustmentItems}).
     */
    public static boolean allTaxableItemsAlreadyTaxed(final Invoice invoice) {
        if (invoice.getInvoiceItems() == null || invoice.getInvoiceItems().isEmpty()) {
            return false;
        }

        final Set<UUID> taxableItemIds = new HashSet<>();
        final Set<UUID> taxedItemIds = taxedLinkedItemIds(invoice);

        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            if (item.getId() == null) {
                if (!InvoiceRequestMapper.isSkippedItemType(item.getInvoiceItemType())) {
                    return false;
                }
                continue;
            }
            if (item.getInvoiceItemType() == InvoiceItemType.TAX) {
                continue;
            }
            if (!InvoiceRequestMapper.isSkippedItemType(item.getInvoiceItemType())) {
                taxableItemIds.add(item.getId());
            }
        }

        if (taxableItemIds.isEmpty()) {
            return false;
        }
        return taxedItemIds.containsAll(taxableItemIds);
    }

    /**
     * Adjustment lines ({@code ITEM_ADJ}/{@code REPAIR_ADJ}) that do not yet have a linked TAX.
     * Used so post-invoice adjustments still get return tax after sales lines are already taxed.
     */
    public static List<InvoiceItem> untaxedAdjustmentItems(final Invoice invoice) {
        final List<InvoiceItem> untaxed = new ArrayList<>();
        if (invoice.getInvoiceItems() == null) {
            return untaxed;
        }
        final Set<UUID> taxedItemIds = taxedLinkedItemIds(invoice);
        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            if (!InvoiceRequestMapper.isReturnAdjustmentItemType(item.getInvoiceItemType())) {
                continue;
            }
            if (item.getId() == null || taxedItemIds.contains(item.getId())) {
                continue;
            }
            untaxed.add(item);
        }
        return untaxed;
    }

    /** True when neither sales nor return tax work remains for this invoice. */
    public static boolean nothingLeftToTax(final Invoice invoice) {
        return allTaxableItemsAlreadyTaxed(invoice) && untaxedAdjustmentItems(invoice).isEmpty();
    }

    private static Set<UUID> taxedLinkedItemIds(final Invoice invoice) {
        final Set<UUID> taxedItemIds = new HashSet<>();
        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            if (item.getInvoiceItemType() == InvoiceItemType.TAX && item.getLinkedItemId() != null) {
                taxedItemIds.add(item.getLinkedItemId());
            }
        }
        return taxedItemIds;
    }
}
