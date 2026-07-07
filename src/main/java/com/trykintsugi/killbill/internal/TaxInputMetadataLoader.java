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

import org.killbill.billing.ObjectType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Loads tax input metadata. Plugin properties take precedence over custom fields.
 */
final class TaxInputMetadataLoader {

    /** @see InvoicePluginPropertyNames#CUSTOMER_USAGE_TYPE */
    static final String CUSTOMER_USAGE_TYPE = InvoicePluginPropertyNames.CUSTOMER_USAGE_TYPE;
    /** @see InvoicePluginPropertyNames#TAX_EXEMPT */
    static final String TAX_EXEMPT = InvoicePluginPropertyNames.TAX_EXEMPT;
    /** Custom field name for per-line tax code. */
    static final String TAX_CODE = "taxCode";

    private TaxInputMetadataLoader() {}

    static TaxInputSnapshot load(
            final OSGIKillbillAPI killbillAPI,
            final Invoice invoice,
            final Iterable<PluginProperty> pluginProperties,
            final TenantContext context) throws Exception {
        final String usageType = resolveCustomerUsageType(killbillAPI, invoice, pluginProperties, context);
        final Optional<Boolean> taxExempt = resolveTaxExempt(killbillAPI, invoice, pluginProperties, context);
        final Map<UUID, String> taxCodes = resolveLineTaxCodes(killbillAPI, invoice, pluginProperties, context);
        final String trn = resolveTaxRegistrationNumber(pluginProperties);
        final String companyName = propertyValue(pluginProperties, InvoicePluginPropertyNames.COMPANY_NAME);
        final TaxAddress shipTo = resolveShipToFromProperties(pluginProperties);
        final boolean shipToFromPluginProperties = shipTo != null;
        return new TaxInputSnapshot(
                usageType, taxExempt, trn, companyName, shipTo, shipToFromPluginProperties, taxCodes);
    }

    private static String resolveCustomerUsageType(
            final OSGIKillbillAPI killbillAPI,
            final Invoice invoice,
            final Iterable<PluginProperty> pluginProperties,
            final TenantContext context) throws Exception {
        final String fromProperty = PluginProperties.findPluginPropertyValue(CUSTOMER_USAGE_TYPE, pluginProperties);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.trim();
        }
        return accountFieldValue(killbillAPI, invoice.getAccountId(), CUSTOMER_USAGE_TYPE, context);
    }

    private static Optional<Boolean> resolveTaxExempt(
            final OSGIKillbillAPI killbillAPI,
            final Invoice invoice,
            final Iterable<PluginProperty> pluginProperties,
            final TenantContext context) throws Exception {
        final String fromProperty = PluginProperties.findPluginPropertyValue(TAX_EXEMPT, pluginProperties);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return Optional.of(Boolean.parseBoolean(fromProperty.trim()));
        }
        final String fromField = accountFieldValue(killbillAPI, invoice.getAccountId(), TAX_EXEMPT, context);
        if (fromField != null && !fromField.isBlank()) {
            return Optional.of(Boolean.parseBoolean(fromField.trim()));
        }
        return Optional.empty();
    }

    private static String resolveTaxRegistrationNumber(final Iterable<PluginProperty> pluginProperties) {
        final String trn = propertyValue(pluginProperties, InvoicePluginPropertyNames.TAX_REGISTRATION_NUMBER);
        if (trn != null) {
            return trn;
        }
        return propertyValue(pluginProperties, InvoicePluginPropertyNames.TRN);
    }

    private static TaxAddress resolveShipToFromProperties(final Iterable<PluginProperty> pluginProperties) {
        final String line1 = propertyValue(pluginProperties, InvoicePluginPropertyNames.SHIP_TO_LINE1);
        final String line2 = propertyValue(pluginProperties, InvoicePluginPropertyNames.SHIP_TO_LINE2);
        final String city = propertyValue(pluginProperties, InvoicePluginPropertyNames.SHIP_TO_CITY);
        final String state = propertyValue(pluginProperties, InvoicePluginPropertyNames.SHIP_TO_STATE);
        final String country = propertyValue(pluginProperties, InvoicePluginPropertyNames.SHIP_TO_COUNTRY);
        final String postalCode = propertyValue(pluginProperties, InvoicePluginPropertyNames.SHIP_TO_POSTAL_CODE);
        if (line1 == null && line2 == null && city == null && state == null && country == null && postalCode == null) {
            return null;
        }
        final TaxAddress address = new TaxAddress(line1, line2, city, state, country, postalCode);
        return address.hasData() ? address : null;
    }

    private static Map<UUID, String> resolveLineTaxCodes(
            final OSGIKillbillAPI killbillAPI,
            final Invoice invoice,
            final Iterable<PluginProperty> pluginProperties,
            final TenantContext context) throws Exception {
        final Set<UUID> invoiceItemIds = new HashSet<>();
        for (final InvoiceItem item : invoice.getInvoiceItems()) {
            if (item.getId() != null) {
                invoiceItemIds.add(item.getId());
            }
        }

        final Map<UUID, String> taxCodes = new HashMap<>();
        if (!invoiceItemIds.isEmpty()) {
            final List<CustomField> customFields = killbillAPI.getCustomFieldUserApi()
                    .getCustomFieldsForAccountType(invoice.getAccountId(), ObjectType.INVOICE_ITEM, context);
            for (final CustomField field : customFields) {
                if (field == null || field.getObjectId() == null) {
                    continue;
                }
                if (!TAX_CODE.equals(field.getFieldName())) {
                    continue;
                }
                if (!invoiceItemIds.contains(field.getObjectId())) {
                    continue;
                }
                final String value = field.getFieldValue();
                if (value != null && !value.isBlank()) {
                    taxCodes.put(field.getObjectId(), value.trim());
                }
            }
        }
        applyPluginPropertyTaxCodes(pluginProperties, taxCodes);
        return taxCodes;
    }

    private static void applyPluginPropertyTaxCodes(
            final Iterable<PluginProperty> pluginProperties,
            final Map<UUID, String> taxCodes) {
        if (pluginProperties == null) {
            return;
        }
        final String prefix = InvoicePluginPropertyNames.TAX_CODE_PREFIX;
        for (final PluginProperty property : pluginProperties) {
            if (property == null || property.getKey() == null) {
                continue;
            }
            if (!property.getKey().startsWith(prefix)) {
                continue;
            }
            try {
                final UUID itemId = UUID.fromString(property.getKey().substring(prefix.length()));
                final String value = stringValue(property.getValue());
                if (value != null) {
                    taxCodes.put(itemId, value);
                }
            } catch (IllegalArgumentException ignored) {
                // Not a UUID suffix — skip.
            }
        }
    }

    private static String stringValue(final Object value) {
        if (value == null) {
            return null;
        }
        final String text = value.toString();
        if (text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private static String propertyValue(
            final Iterable<PluginProperty> pluginProperties,
            final String key) {
        final String value = PluginProperties.findPluginPropertyValue(key, pluginProperties);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String accountFieldValue(
            final OSGIKillbillAPI killbillAPI,
            final UUID accountId,
            final String fieldName,
            final TenantContext context) throws Exception {
        final List<CustomField> customFields = killbillAPI.getCustomFieldUserApi()
                .getCustomFieldsForObject(accountId, ObjectType.ACCOUNT, context);
        for (final CustomField field : customFields) {
            if (field != null && fieldName.equals(field.getFieldName())) {
                final String value = field.getFieldValue();
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    static final class TaxInputSnapshot {
        private final String customerUsageType;
        private final Optional<Boolean> taxExempt;
        private final String taxRegistrationNumber;
        private final String companyName;
        private final TaxAddress shipToAddress;
        private final boolean shipToFromPluginProperties;
        private final Map<UUID, String> taxCodeByInvoiceItemId;

        TaxInputSnapshot(
                final String customerUsageType,
                final Optional<Boolean> taxExempt,
                final String taxRegistrationNumber,
                final String companyName,
                final TaxAddress shipToAddress,
                final boolean shipToFromPluginProperties,
                final Map<UUID, String> taxCodeByInvoiceItemId) {
            this.customerUsageType = customerUsageType;
            this.taxExempt = taxExempt;
            this.taxRegistrationNumber = taxRegistrationNumber;
            this.companyName = companyName;
            this.shipToAddress = shipToAddress;
            this.shipToFromPluginProperties = shipToFromPluginProperties;
            this.taxCodeByInvoiceItemId = taxCodeByInvoiceItemId;
        }

        String customerUsageType() {
            return customerUsageType;
        }

        boolean taxExempt() {
            return taxExempt.orElse(false);
        }

        boolean taxExemptExplicitlySet() {
            return taxExempt.isPresent();
        }

        String taxRegistrationNumber() {
            return taxRegistrationNumber;
        }

        String companyName() {
            return companyName;
        }

        TaxAddress shipToAddress() {
            return shipToAddress;
        }

        boolean shipToFromPluginProperties() {
            return shipToFromPluginProperties;
        }

        Map<UUID, String> taxCodeByInvoiceItemId() {
            return taxCodeByInvoiceItemId;
        }
    }
}
