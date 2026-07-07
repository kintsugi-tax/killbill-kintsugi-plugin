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

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/** Tax metadata merged from Aviate billing account and/or Kill Bill custom fields. */
public final class AccountTaxMetadata {

    public enum Source {
        /** Custom fields and KB account only (non-Aviate deployment). */
        CUSTOM_FIELDS,
        /** Aviate billing account (with optional custom-field overlays). */
        AVIATE_BILLING_ACCOUNT
    }

    private final Source source;
    private final boolean taxExempt;
    private final String customerUsageType;
    private final String taxRegistrationNumber;
    private final String companyName;
    private final String contactEmail;
    private final TaxAddress shipToAddress;
    private final Map<UUID, String> taxCodeByInvoiceItemId;

    private AccountTaxMetadata(
            final Source source,
            final boolean taxExempt,
            final String customerUsageType,
            final String taxRegistrationNumber,
            final String companyName,
            final String contactEmail,
            final TaxAddress shipToAddress,
            final Map<UUID, String> taxCodeByInvoiceItemId) {
        this.source = source;
        this.taxExempt = taxExempt;
        this.customerUsageType = customerUsageType;
        this.taxRegistrationNumber = taxRegistrationNumber;
        this.companyName = companyName;
        this.contactEmail = contactEmail;
        this.shipToAddress = shipToAddress;
        this.taxCodeByInvoiceItemId = taxCodeByInvoiceItemId != null
                ? Map.copyOf(taxCodeByInvoiceItemId)
                : Collections.emptyMap();
    }

    public static AccountTaxMetadata empty() {
        return new AccountTaxMetadata(
                Source.CUSTOM_FIELDS,
                false,
                null,
                null,
                null,
                null,
                null,
                Collections.emptyMap());
    }

    public static Builder builder() {
        return new Builder();
    }

    public Source getSource() {
        return source;
    }

    public boolean isTaxExempt() {
        return taxExempt;
    }

    public String getCustomerUsageType() {
        return customerUsageType;
    }

    public String getTaxRegistrationNumber() {
        return taxRegistrationNumber;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public TaxAddress getShipToAddress() {
        return shipToAddress;
    }

    public String taxCodeForItem(final UUID invoiceItemId) {
        if (invoiceItemId == null) {
            return null;
        }
        return taxCodeByInvoiceItemId.get(invoiceItemId);
    }

    public static final class Builder {
        private Source source = Source.CUSTOM_FIELDS;
        private boolean taxExempt;
        private String customerUsageType;
        private String taxRegistrationNumber;
        private String companyName;
        private String contactEmail;
        private TaxAddress shipToAddress;
        private Map<UUID, String> taxCodeByInvoiceItemId = Collections.emptyMap();

        public Builder source(final Source value) {
            this.source = value;
            return this;
        }

        public Builder taxExempt(final boolean value) {
            this.taxExempt = value;
            return this;
        }

        public Builder customerUsageType(final String value) {
            this.customerUsageType = value;
            return this;
        }

        public Builder taxRegistrationNumber(final String value) {
            this.taxRegistrationNumber = value;
            return this;
        }

        public Builder companyName(final String value) {
            this.companyName = value;
            return this;
        }

        public Builder contactEmail(final String value) {
            this.contactEmail = value;
            return this;
        }

        public Builder shipToAddress(final TaxAddress value) {
            this.shipToAddress = value;
            return this;
        }

        public Builder taxCodeByInvoiceItemId(final Map<UUID, String> value) {
            this.taxCodeByInvoiceItemId = value != null ? Map.copyOf(value) : Collections.emptyMap();
            return this;
        }

        public AccountTaxMetadata build() {
            return new AccountTaxMetadata(
                    source,
                    taxExempt,
                    customerUsageType,
                    taxRegistrationNumber,
                    companyName,
                    contactEmail,
                    shipToAddress,
                    taxCodeByInvoiceItemId);
        }
    }
}
