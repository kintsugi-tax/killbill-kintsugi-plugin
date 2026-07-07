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

import com.trykintsugi.killbill.KintsugiTenantConfig;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.callcontext.TenantContext;

import java.util.Optional;

/**
 * Resolves tax metadata for both deployment models (one plugin):
 * <ol>
 *   <li><b>Plugin properties</b> — primary; Aviate plugin will pass these (AvaTax pattern)</li>
 *   <li><b>Custom fields</b> — fallback for non-Aviate tenants</li>
 *   <li><b>Aviate billing account HTTP</b> — optional gap-fill when {@code aviateIdToken} is set</li>
 * </ol>
 */
public final class TaxMetadataResolver {

    /** @see InvoicePluginPropertyNames#CUSTOMER_USAGE_TYPE */
    public static final String CUSTOMER_USAGE_TYPE = InvoicePluginPropertyNames.CUSTOMER_USAGE_TYPE;
    /** @see InvoicePluginPropertyNames#TAX_EXEMPT */
    public static final String TAX_EXEMPT = InvoicePluginPropertyNames.TAX_EXEMPT;
    /** Custom field name for per-line tax code. */
    public static final String TAX_CODE = TaxInputMetadataLoader.TAX_CODE;

    private final OSGIKillbillAPI killbillAPI;
    private final AviateBillingAccountClient aviateClient;

    public TaxMetadataResolver(final OSGIKillbillAPI killbillAPI) {
        this(killbillAPI, new AviateBillingAccountClient());
    }

    TaxMetadataResolver(
            final OSGIKillbillAPI killbillAPI,
            final AviateBillingAccountClient aviateClient) {
        this.killbillAPI = killbillAPI;
        this.aviateClient = aviateClient;
    }

    public AccountTaxMetadata resolve(
            final Invoice invoice,
            final Account account,
            final Iterable<PluginProperty> pluginProperties,
            final TenantContext context,
            final KintsugiTenantConfig config,
            final String tenantApiKey,
            final String tenantApiSecret) throws Exception {
        final TaxInputMetadataLoader.TaxInputSnapshot input = TaxInputMetadataLoader.load(
                killbillAPI, invoice, pluginProperties, context);

        AccountTaxMetadata.Builder builder = fromInput(input);

        if (config != null
                && config.hasAviateIntegration()
                && !input.shipToFromPluginProperties()) {
            final Optional<AviateBillingAccount> billingAccount = aviateClient.fetchForKbAccountId(
                    config.getKillbillUrl(),
                    invoice.getAccountId(),
                    tenantApiKey,
                    tenantApiSecret,
                    config.getAviateIdToken());
            if (billingAccount.isPresent()) {
                builder = fillAviateGaps(builder, billingAccount.get(), account, input);
            }
        }

        return builder.build();
    }

    private static AccountTaxMetadata.Builder fromInput(
            final TaxInputMetadataLoader.TaxInputSnapshot input) {
        return AccountTaxMetadata.builder()
                .source(AccountTaxMetadata.Source.CUSTOM_FIELDS)
                .taxExempt(input.taxExempt())
                .customerUsageType(input.customerUsageType())
                .taxRegistrationNumber(input.taxRegistrationNumber())
                .companyName(input.companyName())
                .shipToAddress(input.shipToAddress())
                .taxCodeByInvoiceItemId(input.taxCodeByInvoiceItemId());
    }

    private static AccountTaxMetadata.Builder fillAviateGaps(
            final AccountTaxMetadata.Builder builder,
            final AviateBillingAccount billingAccount,
            final Account account,
            final TaxInputMetadataLoader.TaxInputSnapshot input) {
        final String preferredCountry = account != null ? account.getCountry() : null;
        builder.source(AccountTaxMetadata.Source.AVIATE_BILLING_ACCOUNT);

        if (!input.taxExemptExplicitlySet() && billingAccount.isTaxExempt()) {
            builder.taxExempt(true);
        }
        if (input.taxRegistrationNumber() == null) {
            builder.taxRegistrationNumber(billingAccount.resolveTaxRegistrationNumber(preferredCountry));
        }
        if (input.companyName() == null) {
            builder.companyName(billingAccount.getCompanyName());
        }
        if (input.shipToAddress() == null) {
            builder.shipToAddress(billingAccount.resolveShipToAddress(preferredCountry));
        }
        if (billingAccount.getEmail() != null) {
            builder.contactEmail(billingAccount.getEmail());
        }
        return builder;
    }
}
