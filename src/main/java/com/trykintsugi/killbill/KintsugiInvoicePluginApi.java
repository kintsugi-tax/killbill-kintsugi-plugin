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

package com.trykintsugi.killbill;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trykintsugi.killbill.internal.AccountTaxMetadata;
import com.trykintsugi.killbill.internal.InvoiceRequestMapper;
import com.trykintsugi.killbill.internal.InvoiceTaxIdempotency;
import com.trykintsugi.killbill.internal.KintsugiTaxClient;
import com.trykintsugi.killbill.internal.TaxItemMapper;
import com.trykintsugi.killbill.internal.TaxMetadataResolver;
import org.joda.time.Period;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApiRetryException;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.invoice.PluginInvoicePluginApi;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Invoice plugin that delegates tax calculation to the Kintsugi tax API.
 *
 * <p>Sales path: taxable lines → estimate/commit → positive TAX.
 * Return path (AvaTax parity): untaxed {@code ITEM_ADJ}/{@code REPAIR_ADJ} → return
 * estimate → negative TAX linked to the adj item.
 */
public final class KintsugiInvoicePluginApi extends PluginInvoicePluginApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(KintsugiInvoicePluginApi.class);
    private static final List<Period> RETRY_SCHEDULE = List.of(
            Period.minutes(1),
            Period.minutes(5),
            Period.minutes(15));

    private final KintsugiConfigurationHandler configurationHandler;
    private final TaxMetadataResolver taxMetadataResolver;

    public KintsugiInvoicePluginApi(
            final OSGIKillbillAPI killbillAPI,
            final OSGIConfigPropertiesService configProperties,
            final Clock clock,
            final KintsugiConfigurationHandler configurationHandler) {
        super(killbillAPI, configProperties, clock);
        this.configurationHandler = configurationHandler;
        this.taxMetadataResolver = new TaxMetadataResolver(killbillAPI);
    }

    @Override
    public KintsugiAdditionalItemsResult getAdditionalInvoiceItems(
            final Invoice invoice,
            final boolean dryRun,
            final Iterable<PluginProperty> properties,
            final InvoiceContext invoiceContext) {
        final KintsugiTenantConfig config = configurationHandler.getConfigurable(invoiceContext.getTenantId());
        if (config == null || isBlank(config.getKintsugiUrl()) || isBlank(config.getHmacSecret())) {
            LOGGER.warn("Kintsugi plugin not configured for tenant {}", invoiceContext.getTenantId());
            return emptyResult();
        }

        if (invoice.getInvoiceItems() == null || invoice.getInvoiceItems().isEmpty()) {
            return emptyResult();
        }

        final boolean salesNeeded = !InvoiceTaxIdempotency.allTaxableItemsAlreadyTaxed(invoice);
        final List<InvoiceItem> untaxedAdjustments = InvoiceTaxIdempotency.untaxedAdjustmentItems(invoice);
        if (!salesNeeded && untaxedAdjustments.isEmpty()) {
            LOGGER.debug(
                    "Skipping Kintsugi tax for invoice account {} — sales and adjustments already taxed",
                    invoice.getAccountId());
            return emptyResult();
        }

        try {
            final Account account = getAccount(invoice.getAccountId(), invoiceContext);
            final Tenant tenant = killbillAPI.getTenantUserApi().getTenantById(invoiceContext.getTenantId());
            final String tenantApiKey = tenant.getApiKey();
            final AccountTaxMetadata taxMetadata = taxMetadataResolver.resolve(
                    invoice,
                    account,
                    properties,
                    invoiceContext,
                    config,
                    tenantApiKey,
                    tenant.getApiSecret());
            final String tenantIdStr = invoiceContext.getTenantId() != null
                    ? invoiceContext.getTenantId().toString()
                    : null;

            final KintsugiTaxClient client = new KintsugiTaxClient(
                    config.getKintsugiUrl(),
                    config.getHmacSecret(),
                    tenantApiKey);

            final Map<UUID, InvoiceItem> itemsById = TaxItemMapper.indexTaxableItems(invoice);
            final List<InvoiceItem> taxItems = new ArrayList<>();

            if (salesNeeded) {
                final ObjectNode salesBody = InvoiceRequestMapper.toEstimateRequest(
                        invoice, account, dryRun, tenantIdStr, taxMetadata);
                // Defense: never POST an empty sales document (adj/credit-only invoices).
                if (salesBody.path("documents").path(0).path("line_items").size() > 0) {
                    final List<KintsugiTaxClient.TaxLineResult> salesTaxLines =
                            client.estimate(salesBody, !dryRun);
                    taxItems.addAll(TaxItemMapper.toTaxItems(invoice, salesTaxLines, itemsById));
                }
            }

            if (!untaxedAdjustments.isEmpty()) {
                final ObjectNode returnBody = InvoiceRequestMapper.toReturnEstimateRequest(
                        invoice, account, dryRun, tenantIdStr, taxMetadata, untaxedAdjustments);
                if (returnBody != null) {
                    final List<KintsugiTaxClient.TaxLineResult> returnTaxLines =
                            client.estimate(returnBody, !dryRun);
                    taxItems.addAll(TaxItemMapper.toTaxItems(invoice, returnTaxLines, itemsById));
                } else {
                    LOGGER.debug(
                            "No return-tax lines for invoice account {} after lenient skip of unlinked adjs",
                            invoice.getAccountId());
                }
            }

            LOGGER.info(
                    "Kintsugi returned {} tax line(s) for invoice account {} (salesNeeded={}, adjReturns={})",
                    taxItems.size(),
                    invoice.getAccountId(),
                    salesNeeded,
                    untaxedAdjustments.size());

            return new KintsugiAdditionalItemsResult(taxItems);
        } catch (InvoicePluginApiRetryException e) {
            throw e;
        } catch (KintsugiTaxClient.TaxEstimationException e) {
            LOGGER.warn(
                    "Kintsugi tax estimate returned a document error for tenant {}: "
                            + "code={}, document={}, details={}, message={}",
                    invoiceContext.getTenantId(),
                    e.code(),
                    e.documentId(),
                    e.details(),
                    e.getMessage());
            throw new InvoicePluginApiRetryException(e, RETRY_SCHEDULE);
        } catch (Exception e) {
            LOGGER.warn("Kintsugi tax estimate failed for tenant {}: {}", invoiceContext.getTenantId(), e.getMessage());
            throw new InvoicePluginApiRetryException(e, RETRY_SCHEDULE);
        }
    }

    private static KintsugiAdditionalItemsResult emptyResult() {
        return KintsugiAdditionalItemsResult.empty();
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
