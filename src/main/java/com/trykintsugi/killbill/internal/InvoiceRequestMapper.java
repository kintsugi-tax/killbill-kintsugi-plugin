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
import org.joda.time.LocalDate;
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

/** Maps Kill Bill invoice state to a Kintsugi tax estimate request JSON payload. */
public final class InvoiceRequestMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Default product labels for external charges without a plan name. */
    static final String EXTERNAL_CHARGE_CATEGORY = "Physical";
    static final String EXTERNAL_CHARGE_SUBCATEGORY = "General Physical";

    private InvoiceRequestMapper() {}

    public static ObjectNode toEstimateRequest(
            final Invoice invoice,
            final Account account,
            final boolean dryRun,
            final String tenantId) {
        return toEstimateRequest(invoice, account, dryRun, tenantId, AccountTaxMetadata.empty());
    }

    public static ObjectNode toEstimateRequest(
            final Invoice invoice,
            final Account account,
            final boolean dryRun,
            final String tenantId,
            final AccountTaxMetadata taxMetadata) {
        final AccountTaxMetadata metadata = taxMetadata != null ? taxMetadata : AccountTaxMetadata.empty();
        final ArrayNode lineItems = MAPPER.createArrayNode();

        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            if (shouldSkipItem(item)) {
                continue;
            }
            final String externalId = item.getId() != null
                    ? item.getId().toString()
                    : UUID.randomUUID().toString();

            final ObjectNode line = MAPPER.createObjectNode();
            line.put("external_id", externalId);
            line.put("amount", formatAmount(item.getAmount()));
            line.put("quantity", formatQuantity(item.getQuantity()));
            line.put("kind", "item");
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
                line.put("external_product_id", item.getPlanName());
            }
            if (item.getPrettyProductName() != null) {
                line.put("product_name", item.getPrettyProductName());
            } else if (item.getInvoiceItemType() == InvoiceItemType.EXTERNAL_CHARGE) {
                line.put("product_category", EXTERNAL_CHARGE_CATEGORY);
                line.put("product_subcategory", EXTERNAL_CHARGE_SUBCATEGORY);
            }
            final String taxCode = metadata.taxCodeForItem(item.getId());
            if (taxCode != null) {
                line.put("tax_code", taxCode);
            }
            lineItems.add(line);
        }

        final ObjectNode shipTo = resolveAddress(account, metadata);
        final ObjectNode billTo = accountToAddress(account);
        final ObjectNode customer = accountToCustomer(account, metadata);
        final String transactionDate = formatInvoiceDate(invoice);

        final ObjectNode root = KintsugiTaxClient.buildEstimateRequest(
                UUID.randomUUID().toString(),
                invoice.getCurrency().toString(),
                invoice.getId() != null ? invoice.getId().toString() : UUID.randomUUID().toString(),
                invoice.getAccountId().toString(),
                dryRun,
                lineItems,
                shipTo,
                billTo,
                customer,
                transactionDate);

        if (tenantId != null && !tenantId.isBlank()) {
            root.put("tenant_id", tenantId);
        }
        root.put("plugin_name", "killbill-kintsugi");

        if (invoice.getInvoiceNumber() != null) {
            final ObjectNode document = (ObjectNode) root.path("documents").get(0);
            // Stringify — platform invoice_number is str; Jackson ints fail validation.
            document.put("invoice_number", String.valueOf(invoice.getInvoiceNumber()));
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

    /**
     * Item types omitted from the <em>sales</em> estimate payload (TAX + adjustments).
     * Return adjustments are mapped separately via {@link #toReturnEstimateRequest}.
     */
    public static boolean isSkippedItemType(final InvoiceItemType type) {
        return type == InvoiceItemType.TAX
                || type == InvoiceItemType.ITEM_ADJ
                || type == InvoiceItemType.CREDIT_ADJ
                || type == InvoiceItemType.REPAIR_ADJ;
    }

    /**
     * AvaTax-parity return adjustment types. {@code CREDIT_ADJ} is excluded (same as AvaTax
     * {@code PluginTaxCalculator}).
     */
    public static boolean isReturnAdjustmentItemType(final InvoiceItemType type) {
        return type == InvoiceItemType.ITEM_ADJ || type == InvoiceItemType.REPAIR_ADJ;
    }

    /**
     * Builds a return-tax estimate for untaxed {@code ITEM_ADJ}/{@code REPAIR_ADJ} lines.
     * Document id is {@code {invoiceId}:adj-return} so platform commit can skip SALE persist.
     *
     * <p>Lenient mode (MVP): adjustments without a resolvable linked taxable item are skipped.
     */
    public static ObjectNode toReturnEstimateRequest(
            final Invoice invoice,
            final Account account,
            final boolean dryRun,
            final String tenantId,
            final AccountTaxMetadata taxMetadata,
            final List<InvoiceItem> untaxedAdjustments) {
        final AccountTaxMetadata metadata = taxMetadata != null ? taxMetadata : AccountTaxMetadata.empty();
        final Map<UUID, InvoiceItem> itemsById = indexItemsById(invoice);
        final ArrayNode lineItems = MAPPER.createArrayNode();

        for (final InvoiceItem adj : untaxedAdjustments) {
            if (adj.getId() == null) {
                continue;
            }
            // Lenient: missing linked original → skip (AvaTax adjustments.lenientMode=true).
            if (adj.getLinkedItemId() == null || !itemsById.containsKey(adj.getLinkedItemId())) {
                continue;
            }
            final InvoiceItem linked = itemsById.get(adj.getLinkedItemId());
            BigDecimal amount = adj.getAmount() != null ? adj.getAmount() : BigDecimal.ZERO;
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                amount = amount.negate();
            }
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            final ObjectNode line = MAPPER.createObjectNode();
            line.put("external_id", adj.getId().toString());
            line.put("amount", formatAmount(amount));
            line.put("quantity", "1");
            line.put("kind", "item");
            line.put("invoice_item_id", adj.getId().toString());
            if (adj.getInvoiceItemType() != null) {
                line.put("item_type", adj.getInvoiceItemType().name());
            }
            if (adj.getDescription() != null) {
                line.put("description", adj.getDescription());
            } else {
                line.put("description", "Invoice item adjustment");
            }
            // Inherit product identity from the linked taxable line for rate classification.
            if (linked.getPlanName() != null) {
                line.put("plan_name", linked.getPlanName());
                line.put("external_product_id", linked.getPlanName());
            }
            if (linked.getPrettyProductName() != null) {
                line.put("product_name", linked.getPrettyProductName());
            }
            final String taxCode = metadata.taxCodeForItem(linked.getId());
            if (taxCode != null) {
                line.put("tax_code", taxCode);
            }
            line.put("linked_invoice_item_id", adj.getLinkedItemId().toString());
            lineItems.add(line);
        }

        if (lineItems.isEmpty()) {
            return null;
        }

        final String documentId = (invoice.getId() != null ? invoice.getId().toString() : UUID.randomUUID().toString())
                + ":adj-return";
        final ObjectNode shipTo = resolveAddress(account, metadata);
        final ObjectNode billTo = accountToAddress(account);
        final ObjectNode customer = accountToCustomer(account, metadata);
        final String transactionDate = formatInvoiceDate(invoice);

        final ObjectNode root = KintsugiTaxClient.buildEstimateRequest(
                UUID.randomUUID().toString(),
                invoice.getCurrency().toString(),
                documentId,
                invoice.getAccountId().toString(),
                dryRun,
                lineItems,
                shipTo,
                billTo,
                customer,
                transactionDate);

        if (tenantId != null && !tenantId.isBlank()) {
            root.put("tenant_id", tenantId);
        }
        root.put("plugin_name", "killbill-kintsugi");

        // Always attach document_kind; stringify invoice_number (Jackson would
        // otherwise emit a JSON number that Pydantic str fields reject).
        final ObjectNode document = (ObjectNode) root.path("documents").get(0);
        document.put("document_kind", "return");
        if (invoice.getInvoiceNumber() != null) {
            document.put("invoice_number", String.valueOf(invoice.getInvoiceNumber()));
        }

        return root;
    }

    private static Map<UUID, InvoiceItem> indexItemsById(final Invoice invoice) {
        final Map<UUID, InvoiceItem> byId = new HashMap<>();
        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            if (item.getId() != null) {
                byId.put(item.getId(), item);
            }
        }
        return byId;
    }

    private static boolean shouldSkipItem(final InvoiceItem item) {
        return isSkippedItemType(item.getInvoiceItemType());
    }

    private static ObjectNode resolveAddress(final Account account, final AccountTaxMetadata metadata) {
        if (metadata.getShipToAddress() != null && metadata.getShipToAddress().hasData()) {
            return metadata.getShipToAddress().toJson(MAPPER);
        }
        return accountToAddress(account);
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

    private static ObjectNode accountToCustomer(final Account account, final AccountTaxMetadata metadata) {
        final ObjectNode customer = MAPPER.createObjectNode();
        final String externalId = account.getExternalKey() != null
                ? account.getExternalKey()
                : account.getId().toString();
        customer.put("external_id", externalId);
        final String email = metadata.getContactEmail() != null
                ? metadata.getContactEmail()
                : account.getEmail();
        if (email != null) {
            customer.put("email", email);
        }
        final String name = metadata.getCompanyName() != null
                ? metadata.getCompanyName()
                : account.getName();
        if (name != null) {
            customer.put("name", name);
        }
        if (metadata.isTaxExempt()) {
            customer.put("exempt", true);
        }
        if (metadata.getCustomerUsageType() != null) {
            customer.put("entity_use_code", metadata.getCustomerUsageType());
        }
        if (metadata.getTaxRegistrationNumber() != null) {
            customer.put("tax_registration_number", metadata.getTaxRegistrationNumber());
        }
        return customer;
    }

    static String formatInvoiceDate(final Invoice invoice) {
        final LocalDate invoiceDate = invoice.getInvoiceDate();
        if (invoiceDate == null) {
            return LocalDate.now().toString();
        }
        return invoiceDate.toString();
    }

    static String formatAmount(final BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String formatQuantity(final BigDecimal quantity) {
        if (quantity == null) {
            return "1";
        }
        return quantity.stripTrailingZeros().toPlainString();
    }
}
