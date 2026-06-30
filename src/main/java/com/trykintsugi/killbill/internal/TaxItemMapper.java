package com.trykintsugi.killbill.internal;

import org.joda.time.LocalDate;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.plugin.api.invoice.PluginInvoiceItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Maps Kintsugi tax lines to Kill Bill TAX invoice items. */
public final class TaxItemMapper {

    private TaxItemMapper() {}

    public static List<InvoiceItem> toTaxItems(
            final Invoice invoice,
            final List<KintsugiTaxClient.TaxLineResult> taxLines,
            final Map<UUID, InvoiceItem> taxableItemsByExternalId) {
        final List<InvoiceItem> taxItems = new ArrayList<>();
        final LocalDate invoiceDate = invoice.getInvoiceDate();

        for (final KintsugiTaxClient.TaxLineResult taxLine : taxLines) {
            if (taxLine.taxAmount() == null || taxLine.taxAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            final InvoiceItem linkedItem = taxableItemsByExternalId.get(
                    UUID.fromString(taxLine.lineExternalId()));
            if (linkedItem == null) {
                // external_id may be the string form of item id — try direct lookup
                final InvoiceItem fallback = findByExternalId(taxableItemsByExternalId, taxLine.lineExternalId());
                if (fallback == null) {
                    continue;
                }
                taxItems.add(buildTaxItem(invoice, fallback, invoiceDate, taxLine));
            } else {
                taxItems.add(buildTaxItem(invoice, linkedItem, invoiceDate, taxLine));
            }
        }
        return taxItems;
    }

    public static Map<UUID, InvoiceItem> indexTaxableItems(final Invoice invoice) {
        final Map<UUID, InvoiceItem> byExternalId = new HashMap<>();
        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            if (item.getId() != null) {
                byExternalId.put(item.getId(), item);
            }
        }
        return byExternalId;
    }

    private static InvoiceItem findByExternalId(
            final Map<UUID, InvoiceItem> items,
            final String externalId) {
        try {
            return items.get(UUID.fromString(externalId));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static InvoiceItem buildTaxItem(
            final Invoice invoice,
            final InvoiceItem linkedItem,
            final LocalDate invoiceDate,
            final KintsugiTaxClient.TaxLineResult taxLine) {
        final String description = taxLine.ratePercent() != null
                ? String.format("Sales tax (%.2f%%)", taxLine.ratePercent())
                : "Sales tax";
        return PluginInvoiceItem.createTaxItem(
                linkedItem,
                invoice.getId(),
                invoiceDate,
                null,
                taxLine.taxAmount(),
                description);
    }
}
