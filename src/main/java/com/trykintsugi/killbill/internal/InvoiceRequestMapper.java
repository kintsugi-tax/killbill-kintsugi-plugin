package com.trykintsugi.killbill.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Maps Kill Bill invoice state to Kintsugi estimate request JSON. */
public final class InvoiceRequestMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Kintsugi public product API labels for generic external charges. */
    static final String EXTERNAL_CHARGE_CATEGORY = "Physical";
    static final String EXTERNAL_CHARGE_SUBCATEGORY = "General Physical";

    private InvoiceRequestMapper() {}

    public static ObjectNode toEstimateRequest(
            final Invoice invoice,
            final Account account,
            final boolean dryRun,
            final String tenantId) {
        final ArrayNode lineItems = MAPPER.createArrayNode();
        final Map<UUID, String> externalIds = new HashMap<>();

        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            if (shouldSkipItem(item)) {
                continue;
            }
            final String externalId = item.getId() != null
                    ? item.getId().toString()
                    : UUID.randomUUID().toString();
            externalIds.put(item.getId(), externalId);

            final ObjectNode line = MAPPER.createObjectNode();
            line.put("external_id", externalId);
            line.put("amount", formatAmount(item.getAmount()));
            if (item.getDescription() != null) {
                line.put("description", item.getDescription());
            }
            if (item.getId() != null) {
                line.put("invoice_item_id", item.getId().toString());
            }
            if (item.getInvoiceItemType() != null) {
                line.put("item_type", item.getInvoiceItemType().name());
            }
            if (item.getPlanName() != null) {
                line.put("plan_name", item.getPlanName());
            }
            if (item.getPrettyProductName() != null) {
                line.put("product_name", item.getPrettyProductName());
            }
            if (item.getPlanName() != null) {
                line.put("external_product_id", item.getPlanName());
            } else if (item.getInvoiceItemType() == InvoiceItemType.EXTERNAL_CHARGE) {
                line.put("product_category", EXTERNAL_CHARGE_CATEGORY);
                line.put("product_subcategory", EXTERNAL_CHARGE_SUBCATEGORY);
            }
            lineItems.add(line);
        }

        final ObjectNode shipTo = accountToAddress(account);
        final ObjectNode root = KintsugiTaxClient.buildEstimateRequest(
                UUID.randomUUID().toString(),
                invoice.getCurrency().toString(),
                invoice.getId() != null ? invoice.getId().toString() : UUID.randomUUID().toString(),
                invoice.getAccountId().toString(),
                dryRun,
                lineItems,
                shipTo);

        if (tenantId != null && !tenantId.isBlank()) {
            root.put("tenant_id", tenantId);
        }
        root.put("plugin_name", "killbill-kintsugi");

        if (invoice.getInvoiceNumber() != null) {
            final ObjectNode document = (ObjectNode) root.path("documents").get(0);
            document.put("invoice_number", invoice.getInvoiceNumber());
        }

        return root;
    }

    /** Returns external_id keyed by Kill Bill invoice item id for response mapping. */
    public static Map<UUID, String> externalIdsForInvoice(final Invoice invoice) {
        final Map<UUID, String> externalIds = new HashMap<>();
        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            if (shouldSkipItem(item) || item.getId() == null) {
                continue;
            }
            externalIds.put(item.getId(), item.getId().toString());
        }
        return externalIds;
    }

    private static boolean shouldSkipItem(final InvoiceItem item) {
        final InvoiceItemType type = item.getInvoiceItemType();
        return type == InvoiceItemType.TAX
                || type == InvoiceItemType.ITEM_ADJ
                || type == InvoiceItemType.CREDIT_ADJ
                || type == InvoiceItemType.REPAIR_ADJ;
    }

    private static ObjectNode accountToAddress(final Account account) {
        final ObjectNode address = MAPPER.createObjectNode();
        if (account.getCountry() != null) {
            address.put("country", account.getCountry());
        }
        if (account.getPostalCode() != null) {
            address.put("postal_code", account.getPostalCode());
        }
        if (account.getStateOrProvince() != null) {
            address.put("state", account.getStateOrProvince());
        }
        if (account.getCity() != null) {
            address.put("city", account.getCity());
        }
        if (account.getAddress1() != null) {
            address.put("line1", account.getAddress1());
        }
        if (account.getAddress2() != null) {
            address.put("line2", account.getAddress2());
        }
        return address;
    }

    static String formatAmount(final BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
