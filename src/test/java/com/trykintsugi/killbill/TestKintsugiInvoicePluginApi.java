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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.trykintsugi.killbill.internal.KintsugiTaxClient;
import org.joda.time.Period;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApiRetryException;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.plugin.TestUtils;
import org.killbill.billing.plugin.api.PluginCallContext;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.ObjectType;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public class TestKintsugiInvoicePluginApi {

    private static final String HMAC_SECRET = "test-hmac-secret";
    private static final String TENANT_API_KEY = "tenant-api-key";

    private WireMockServer wireMockServer;
    private KintsugiInvoicePluginApi pluginApi;
    private KintsugiConfigurationHandler configurationHandler;
    private Account account;
    private UUID tenantId;
    private InvoiceContext invoiceContext;

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();

        final Clock clock = new DefaultClock();
        account = TestUtils.buildAccount(
                Currency.USD, "1 Market St", null, "Austin", "TX", "78701", "US");
        tenantId = UUID.randomUUID();
        final CallContext callContext = new PluginCallContext(
                KintsugiActivator.PLUGIN_NAME, clock.getUTCNow(), account.getId(), tenantId);
        invoiceContext = new TestInvoiceContext(null, null, null, false, false, callContext);

        final OSGIKillbillAPI osgiKillbillAPI = TestUtils.buildOSGIKillbillAPI(account);
        final TenantUserApi tenantUserApi = Mockito.mock(TenantUserApi.class);
        final Tenant tenant = Mockito.mock(Tenant.class);
        Mockito.when(tenant.getApiKey()).thenReturn(TENANT_API_KEY);
        Mockito.when(tenant.getApiSecret()).thenReturn("tenant-api-secret");
        Mockito.when(tenantUserApi.getTenantById(tenantId)).thenReturn(tenant);
        Mockito.when(osgiKillbillAPI.getTenantUserApi()).thenReturn(tenantUserApi);

        final CustomFieldUserApi customFieldUserApi = Mockito.mock(CustomFieldUserApi.class);
        Mockito.when(osgiKillbillAPI.getCustomFieldUserApi()).thenReturn(customFieldUserApi);
        Mockito.when(customFieldUserApi.getCustomFieldsForObject(
                Mockito.any(), Mockito.eq(ObjectType.ACCOUNT), Mockito.any()))
                .thenReturn(List.of());
        Mockito.when(customFieldUserApi.getCustomFieldsForAccountType(
                Mockito.any(), Mockito.eq(ObjectType.INVOICE_ITEM), Mockito.any()))
                .thenReturn(List.of());

        configurationHandler = new KintsugiConfigurationHandler(
                KintsugiActivator.PLUGIN_NAME, osgiKillbillAPI);
        configurePlugin(wireMockBaseUrl());

        pluginApi = new KintsugiInvoicePluginApi(
                osgiKillbillAPI,
                new OSGIConfigPropertiesService(Mockito.mock(BundleContext.class)),
                clock,
                configurationHandler);
    }

    @AfterMethod(groups = "fast")
    public void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test(groups = "fast")
    public void testMissingConfigReturnsEmpty() {
        final Properties properties = new Properties();
        properties.setProperty("kintsugiUrl", "");
        properties.setProperty("hmacSecret", "");
        configurationHandler.setDefaultConfigurable(configurationHandler.createConfigurable(properties));

        final Invoice invoice = TestUtils.buildInvoice(account);
        final List<InvoiceItem> invoiceItems = new LinkedList<>();
        Mockito.when(invoice.getInvoiceItems()).thenReturn(invoiceItems);
        invoiceItems.add(TestUtils.buildInvoiceItem(
                invoice, InvoiceItemType.EXTERNAL_CHARGE, new BigDecimal("100"), null));

        Assert.assertTrue(pluginApi.getAdditionalInvoiceItems(
                invoice, true, List.of(), invoiceContext).getAdditionalItems().isEmpty());
    }

    @Test(groups = "fast")
    public void testEmptyInvoiceReturnsEmpty() {
        final Invoice invoice = TestUtils.buildInvoice(account);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of());

        Assert.assertTrue(pluginApi.getAdditionalInvoiceItems(
                invoice, true, List.of(), invoiceContext).getAdditionalItems().isEmpty());
    }

    @Test(groups = "fast")
    public void testExternalChargeReturnsTaxItems() throws Exception {
        final UUID itemId = UUID.randomUUID();
        stubTaxResponse(itemId, "8.25", "/killbill/tax/estimate");

        final Invoice invoice = buildInvoiceWithExternalCharge(itemId, new BigDecimal("100"));

        final List<InvoiceItem> taxItems = pluginApi.getAdditionalInvoiceItems(
                invoice, true, List.of(), invoiceContext).getAdditionalItems();

        Assert.assertEquals(taxItems.size(), 1);
        Assert.assertEquals(taxItems.get(0).getInvoiceItemType(), InvoiceItemType.TAX);
        Assert.assertEquals(taxItems.get(0).getAmount(), new BigDecimal("8.25"));

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/killbill/tax/estimate"))
                .withHeader("X-Killbill-ApiKey", equalTo(TENANT_API_KEY))
                .withHeader("X-Killbill-Kintsugi-Signature", matching("[a-f0-9]{64}")));
    }

    @Test(groups = "fast")
    public void testMultipleLineItemsReturnMultipleTaxItems() throws Exception {
        final UUID itemId1 = UUID.randomUUID();
        final UUID itemId2 = UUID.randomUUID();
        final String body = "{\"documents\":[{\"line_items\":["
                + "{\"external_id\":\"" + itemId1 + "\",\"tax_amount\":\"1.00\",\"rate\":\"8.25\"},"
                + "{\"external_id\":\"" + itemId2 + "\",\"tax_amount\":\"2.50\",\"rate\":\"8.25\"}"
                + "]}]}";
        wireMockServer.stubFor(post(urlPathEqualTo("/killbill/tax/estimate"))
                .willReturn(aResponse().withStatus(200).withBody(body)));

        final Invoice invoice = TestUtils.buildInvoice(account);
        final List<InvoiceItem> invoiceItems = new LinkedList<>();
        Mockito.when(invoice.getInvoiceItems()).thenReturn(invoiceItems);

        final InvoiceItem item1 = TestUtils.buildInvoiceItem(
                invoice, InvoiceItemType.EXTERNAL_CHARGE, new BigDecimal("10"), null);
        Mockito.when(item1.getId()).thenReturn(itemId1);
        final InvoiceItem item2 = TestUtils.buildInvoiceItem(
                invoice, InvoiceItemType.EXTERNAL_CHARGE, new BigDecimal("25"), null);
        Mockito.when(item2.getId()).thenReturn(itemId2);
        invoiceItems.add(item1);
        invoiceItems.add(item2);

        final List<InvoiceItem> taxItems = pluginApi.getAdditionalInvoiceItems(
                invoice, true, List.of(), invoiceContext).getAdditionalItems();

        Assert.assertEquals(taxItems.size(), 2);
        Assert.assertEquals(taxItems.get(0).getAmount(), new BigDecimal("1.00"));
        Assert.assertEquals(taxItems.get(1).getAmount(), new BigDecimal("2.50"));
    }

    @Test(groups = "fast")
    public void testDryRunUsesEstimateEndpoint() throws Exception {
        final UUID itemId = UUID.randomUUID();
        stubTaxResponse(itemId, "1.00", "/killbill/tax/estimate");

        final Invoice invoice = buildInvoiceWithExternalCharge(itemId, new BigDecimal("50"));

        pluginApi.getAdditionalInvoiceItems(invoice, true, List.of(), invoiceContext);

        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/killbill/tax/estimate")));
        wireMockServer.verify(0, postRequestedFor(urlPathEqualTo("/killbill/tax/commit")));
    }

    @Test(groups = "fast")
    public void testCommitUsesCommitEndpoint() throws Exception {
        final UUID itemId = UUID.randomUUID();
        stubTaxResponse(itemId, "1.00", "/killbill/tax/commit");

        final Invoice invoice = buildInvoiceWithExternalCharge(itemId, new BigDecimal("50"));

        pluginApi.getAdditionalInvoiceItems(invoice, false, List.of(), invoiceContext);

        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/killbill/tax/commit")));
        wireMockServer.verify(0, postRequestedFor(urlPathEqualTo("/killbill/tax/estimate")));
    }

    @Test(groups = "fast", expectedExceptions = InvoicePluginApiRetryException.class)
    public void testApiFailureTriggersRetry() throws Exception {
        wireMockServer.stubFor(post(urlPathEqualTo("/killbill/tax/estimate"))
                .willReturn(aResponse().withStatus(503).withBody("{\"error\":\"unavailable\"}")));

        final UUID itemId = UUID.randomUUID();
        final Invoice invoice = buildInvoiceWithExternalCharge(itemId, new BigDecimal("100"));

        pluginApi.getAdditionalInvoiceItems(invoice, true, List.of(), invoiceContext);
    }

    @Test(groups = "fast")
    public void testDocumentErrorTriggersRetryWithExistingSchedule() throws Exception {
        final String body = "{\"documents\":[{\"document_id\":\"invoice-1\","
                + "\"error\":{\"code\":\"PRODUCTS_NOT_FOUND\","
                + "\"message\":\"Product p2-monthly not found\","
                + "\"details\":\"ProductNotFound\"}}]}";
        wireMockServer.stubFor(post(urlPathEqualTo("/killbill/tax/estimate"))
                .willReturn(aResponse().withStatus(200).withBody(body)));

        final UUID itemId = UUID.randomUUID();
        final Invoice invoice = buildInvoiceWithExternalCharge(itemId, new BigDecimal("100"));

        try {
            pluginApi.getAdditionalInvoiceItems(invoice, true, List.of(), invoiceContext);
            Assert.fail("Expected document-level tax estimation error to trigger retry");
        } catch (InvoicePluginApiRetryException e) {
            Assert.assertTrue(e.getCause() instanceof KintsugiTaxClient.TaxEstimationException);
            Assert.assertEquals(
                    e.getRetrySchedule(),
                    List.of(Period.minutes(1), Period.minutes(5), Period.minutes(15)));
        }
    }

    @Test(groups = "fast")
    public void testSignatureHeaderIsPresent() throws Exception {
        final UUID itemId = UUID.randomUUID();
        stubTaxResponse(itemId, "2.00", "/killbill/tax/estimate");

        final Invoice invoice = buildInvoiceWithExternalCharge(itemId, new BigDecimal("25"));

        pluginApi.getAdditionalInvoiceItems(invoice, true, List.of(), invoiceContext);

        final LoggedRequest request = wireMockServer.findAll(postRequestedFor(urlPathEqualTo("/killbill/tax/estimate")))
                .get(0);
        final String signature = request.getHeader("X-Killbill-Kintsugi-Signature");
        Assert.assertNotNull(signature);
        Assert.assertTrue(signature.matches("[a-f0-9]{64}"));
        Assert.assertFalse(request.getBodyAsString().isEmpty());
    }

    @Test(groups = "fast")
    public void testReInvoiceSkipsApiWhenTaxItemsAlreadyPresent() throws Exception {
        final UUID chargeId = UUID.randomUUID();
        final Invoice invoice = TestUtils.buildInvoice(account);
        final List<InvoiceItem> invoiceItems = new LinkedList<>();
        Mockito.when(invoice.getInvoiceItems()).thenReturn(invoiceItems);

        final InvoiceItem charge = TestUtils.buildInvoiceItem(
                invoice, InvoiceItemType.EXTERNAL_CHARGE, new BigDecimal("100"), null);
        Mockito.when(charge.getId()).thenReturn(chargeId);
        invoiceItems.add(charge);

        final InvoiceItem tax = TestUtils.buildInvoiceItem(
                invoice, InvoiceItemType.TAX, new BigDecimal("8.25"), null);
        Mockito.when(tax.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(tax.getLinkedItemId()).thenReturn(chargeId);
        invoiceItems.add(tax);

        final List<InvoiceItem> result = pluginApi.getAdditionalInvoiceItems(
                invoice, true, List.of(), invoiceContext).getAdditionalItems();

        Assert.assertTrue(result.isEmpty());
        wireMockServer.verify(0, postRequestedFor(urlPathEqualTo("/killbill/tax/estimate")));
        wireMockServer.verify(0, postRequestedFor(urlPathEqualTo("/killbill/tax/commit")));
    }

    private void configurePlugin(final String kintsugiUrl) {
        final Properties properties = new Properties();
        properties.setProperty("kintsugiUrl", kintsugiUrl);
        properties.setProperty("hmacSecret", HMAC_SECRET);
        configurationHandler.setDefaultConfigurable(configurationHandler.createConfigurable(properties));
    }

    private String wireMockBaseUrl() {
        return "http://localhost:" + wireMockServer.port();
    }

    private Invoice buildInvoiceWithExternalCharge(final UUID itemId, final BigDecimal amount) {
        final Invoice invoice = TestUtils.buildInvoice(account);
        final List<InvoiceItem> invoiceItems = new LinkedList<>();
        Mockito.when(invoice.getInvoiceItems()).thenReturn(invoiceItems);

        final InvoiceItem item = TestUtils.buildInvoiceItem(
                invoice, InvoiceItemType.EXTERNAL_CHARGE, amount, null);
        Mockito.when(item.getId()).thenReturn(itemId);
        invoiceItems.add(item);
        return invoice;
    }

    private void stubTaxResponse(final UUID itemId, final String taxAmount, final String path) {
        final String body = "{\"documents\":[{\"line_items\":[{\"external_id\":\""
                + itemId + "\",\"tax_amount\":\"" + taxAmount + "\",\"rate\":\"8.25\"}]}]}";
        wireMockServer.stubFor(post(urlPathEqualTo(path)).willReturn(aResponse().withStatus(200).withBody(body)));
    }
}
