package com.trykintsugi.killbill;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trykintsugi.killbill.internal.InvoiceRequestMapper;
import com.trykintsugi.killbill.internal.KintsugiTaxClient;
import com.trykintsugi.killbill.internal.TaxItemMapper;
import org.joda.time.Period;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.plugin.api.AdditionalItemsResult;
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApiRetryException;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.invoice.PluginInvoicePluginApi;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Invoice plugin that delegates tax calculation to Kintsugi L2 API. */
public final class KintsugiInvoicePluginApi extends PluginInvoicePluginApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(KintsugiInvoicePluginApi.class);
    private static final List<Period> RETRY_SCHEDULE = List.of(
            Period.minutes(1),
            Period.minutes(5),
            Period.minutes(15));

    private final KintsugiConfigurationHandler configurationHandler;

    public KintsugiInvoicePluginApi(
            final OSGIKillbillAPI killbillAPI,
            final OSGIConfigPropertiesService configProperties,
            final Clock clock,
            final KintsugiConfigurationHandler configurationHandler) {
        super(killbillAPI, configProperties, clock);
        this.configurationHandler = configurationHandler;
    }

    @Override
    public AdditionalItemsResult getAdditionalInvoiceItems(
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

        try {
            final Account account = getAccount(invoice.getAccountId(), invoiceContext);
            final String tenantApiKey = resolveTenantApiKey(invoiceContext);
            final ObjectNode requestBody = InvoiceRequestMapper.toEstimateRequest(
                    invoice,
                    account,
                    dryRun,
                    invoiceContext.getTenantId() != null ? invoiceContext.getTenantId().toString() : null);

            final KintsugiTaxClient client = new KintsugiTaxClient(
                    config.getKintsugiUrl(),
                    config.getHmacSecret(),
                    tenantApiKey);

            final List<KintsugiTaxClient.TaxLineResult> taxLines =
                    client.estimate(requestBody, !dryRun);

            final Map<UUID, InvoiceItem> taxableById = TaxItemMapper.indexTaxableItems(invoice);
            final List<InvoiceItem> taxItems = TaxItemMapper.toTaxItems(invoice, taxLines, taxableById);

            LOGGER.info(
                    "Kintsugi returned {} tax line(s) for invoice account {}",
                    taxItems.size(),
                    invoice.getAccountId());

            return new AdditionalItemsResult() {
                @Override
                public List<InvoiceItem> getAdditionalItems() {
                    return taxItems;
                }

                @Override
                public Iterable<PluginProperty> getAdjustedPluginProperties() {
                    return null;
                }
            };
        } catch (InvoicePluginApiRetryException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Kintsugi tax estimate failed for tenant {}: {}", invoiceContext.getTenantId(), e.getMessage());
            throw new InvoicePluginApiRetryException(e, RETRY_SCHEDULE);
        }
    }

    private String resolveTenantApiKey(final TenantContext tenantContext) throws Exception {
        final Tenant tenant = killbillAPI.getTenantUserApi().getTenantById(tenantContext.getTenantId());
        return tenant.getApiKey();
    }

    private static AdditionalItemsResult emptyResult() {
        return new AdditionalItemsResult() {
            @Override
            public List<InvoiceItem> getAdditionalItems() {
                return Collections.emptyList();
            }

            @Override
            public Iterable<PluginProperty> getAdjustedPluginProperties() {
                return null;
            }
        };
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
