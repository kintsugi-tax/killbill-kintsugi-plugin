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

/**
 * Plugin property names for invoice tax metadata.
 *
 * <p>AvaTax-compatible names are used where possible. The Aviate plugin is expected to pass these
 * on invoice generation (see Kill Bill team discussion); custom fields remain the fallback for
 * non-Aviate deployments.
 */
public final class InvoicePluginPropertyNames {

    /** Avalara entity use code. */
    public static final String CUSTOMER_USAGE_TYPE = "customerUsageType";
    /** When {@code true}, customer is tax-exempt. */
    public static final String TAX_EXEMPT = "taxExempt";
    /** Tax registration number (TRN / VAT / EIN). */
    public static final String TAX_REGISTRATION_NUMBER = "taxRegistrationNumber";
    /** Alias for {@link #TAX_REGISTRATION_NUMBER}. */
    public static final String TRN = "trn";
    /** Company or customer display name. */
    public static final String COMPANY_NAME = "companyName";
    /** Per-line tax code prefix; full key is {@code taxCode_<invoiceItemId>} (AvaTax pattern). */
    public static final String TAX_CODE_PREFIX = "taxCode_";
    /** Ship-to address components. */
    public static final String SHIP_TO_LINE1 = "shipToLine1";
    public static final String SHIP_TO_LINE2 = "shipToLine2";
    public static final String SHIP_TO_CITY = "shipToCity";
    public static final String SHIP_TO_STATE = "shipToState";
    public static final String SHIP_TO_COUNTRY = "shipToCountry";
    public static final String SHIP_TO_POSTAL_CODE = "shipToPostalCode";

    private InvoicePluginPropertyNames() {}
}
