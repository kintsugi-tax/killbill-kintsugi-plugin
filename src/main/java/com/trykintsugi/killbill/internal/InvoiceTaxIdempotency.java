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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Detects when tax items were already generated for an invoice (re-invoice idempotency). */
public final class InvoiceTaxIdempotency {

    private InvoiceTaxIdempotency() {}

    /**
     * Returns true when every taxable line on the invoice already has a linked {@code TAX} item.
     * Mirrors Avatax re-invoice behavior (no duplicate tax on second plugin pass).
     */
    public static boolean allTaxableItemsAlreadyTaxed(final Invoice invoice) {
        if (invoice.getInvoiceItems() == null || invoice.getInvoiceItems().isEmpty()) {
            return false;
        }

        final Set<UUID> taxableItemIds = new HashSet<>();
        final Set<UUID> taxedItemIds = new HashSet<>();

        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            if (item.getId() == null) {
                if (!InvoiceRequestMapper.isSkippedItemType(item.getInvoiceItemType())) {
                    return false;
                }
                continue;
            }
            if (item.getInvoiceItemType() == InvoiceItemType.TAX) {
                if (item.getLinkedItemId() != null) {
                    taxedItemIds.add(item.getLinkedItemId());
                }
            } else if (!InvoiceRequestMapper.isSkippedItemType(item.getInvoiceItemType())) {
                taxableItemIds.add(item.getId());
            }
        }

        if (taxableItemIds.isEmpty()) {
            return false;
        }
        return taxedItemIds.containsAll(taxableItemIds);
    }
}
