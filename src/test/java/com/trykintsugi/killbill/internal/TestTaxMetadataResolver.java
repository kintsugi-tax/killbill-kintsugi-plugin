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
import com.github.tomakehurst.wiremock.WireMockServer;
import com.trykintsugi.killbill.KintsugiTenantConfig;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.ObjectType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.PluginCallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.clock.DefaultClock;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public class TestTaxMetadataResolver {

    private static final String TENANT_API_KEY = "tenant-api-key";
    private static final String TENANT_API_SECRET = "tenant-api-secret";
    private static final String AVIATE_TOKEN = "aviate-jwt";

    private WireMockServer wireMockServer;

    @BeforeMethod(groups = "fast")
    public void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterMethod(groups = "fast")
    public void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test(groups = "fast")
    public void testResolvesAccountCustomFieldsWithoutAviate() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final UUID tenantId = UUID.randomUUID();
        final UUID itemId = UUID.randomUUID();

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        Mockito.when(item.getId()).thenReturn(itemId);
        Mockito.when(item.getInvoiceItemType()).thenReturn(InvoiceItemType.EXTERNAL_CHARGE);
        Mockito.when(item.getAmount()).thenReturn(new BigDecimal("10"));
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of(item));

        final CustomField usageField = customField(accountId, ObjectType.ACCOUNT, TaxMetadataResolver.CUSTOMER_USAGE_TYPE, "G");
        final CustomField exemptField = customField(accountId, ObjectType.ACCOUNT, TaxMetadataResolver.TAX_EXEMPT, "true");
        final CustomField taxCodeField = customField(itemId, ObjectType.INVOICE_ITEM, TaxMetadataResolver.TAX_CODE, "P0000000");

        final OSGIKillbillAPI killbillAPI = mockCustomFields(
                accountId, tenantId, List.of(usageField, exemptField), List.of(taxCodeField));
        final TaxMetadataResolver resolver = new TaxMetadataResolver(killbillAPI);
        final KintsugiTenantConfig config = baseConfig();

        final AccountTaxMetadata metadata = resolver.resolve(
                invoice,
                Mockito.mock(Account.class),
                List.<PluginProperty>of(),
                tenantContext(tenantId),
                config,
                TENANT_API_KEY,
                TENANT_API_SECRET);

        Assert.assertEquals(metadata.getSource(), AccountTaxMetadata.Source.CUSTOM_FIELDS);
        Assert.assertTrue(metadata.isTaxExempt());
        Assert.assertEquals(metadata.getCustomerUsageType(), "G");
        Assert.assertEquals(metadata.taxCodeForItem(itemId), "P0000000");
    }

    @Test(groups = "fast")
    public void testPluginPropertiesOverrideAccountFields() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final UUID tenantId = UUID.randomUUID();

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of());

        final OSGIKillbillAPI killbillAPI = Mockito.mock(OSGIKillbillAPI.class);
        final CustomFieldUserApi customFieldUserApi = Mockito.mock(CustomFieldUserApi.class);
        Mockito.when(killbillAPI.getCustomFieldUserApi()).thenReturn(customFieldUserApi);

        final List<PluginProperty> properties = List.of(
                new PluginProperty(TaxMetadataResolver.CUSTOMER_USAGE_TYPE, "A", false),
                new PluginProperty(TaxMetadataResolver.TAX_EXEMPT, "true", false));

        final TaxMetadataResolver resolver = new TaxMetadataResolver(killbillAPI);
        final AccountTaxMetadata metadata = resolver.resolve(
                invoice,
                Mockito.mock(Account.class),
                properties,
                tenantContext(tenantId),
                baseConfig(),
                TENANT_API_KEY,
                TENANT_API_SECRET);

        Assert.assertTrue(metadata.isTaxExempt());
        Assert.assertEquals(metadata.getCustomerUsageType(), "A");
        Mockito.verify(customFieldUserApi, Mockito.never())
                .getCustomFieldsForObject(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test(groups = "fast")
    public void testPluginPropertyTaxCodeOverridesCustomField() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final UUID tenantId = UUID.randomUUID();
        final UUID itemId = UUID.randomUUID();

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        Mockito.when(item.getId()).thenReturn(itemId);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of(item));

        final CustomField taxCodeField = customField(
                itemId, ObjectType.INVOICE_ITEM, TaxMetadataResolver.TAX_CODE, "FROM_FIELD");
        final OSGIKillbillAPI killbillAPI = mockCustomFields(
                accountId, tenantId, List.of(), List.of(taxCodeField));
        final TaxMetadataResolver resolver = new TaxMetadataResolver(killbillAPI);

        final List<PluginProperty> properties = List.of(
                new PluginProperty(InvoicePluginPropertyNames.TAX_CODE_PREFIX + itemId, "FROM_PROPERTY", false));

        final AccountTaxMetadata metadata = resolver.resolve(
                invoice,
                Mockito.mock(Account.class),
                properties,
                tenantContext(tenantId),
                baseConfig(),
                TENANT_API_KEY,
                TENANT_API_SECRET);

        Assert.assertEquals(metadata.taxCodeForItem(itemId), "FROM_PROPERTY");
    }

    @Test(groups = "fast")
    public void testPluginPropertyShipToSkipsAviateHttpLookup() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final UUID tenantId = UUID.randomUUID();

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of());

        final OSGIKillbillAPI killbillAPI = mockCustomFields(accountId, tenantId, List.of(), List.of());
        final TaxMetadataResolver resolver = new TaxMetadataResolver(killbillAPI);

        final List<PluginProperty> properties = List.of(
                new PluginProperty(InvoicePluginPropertyNames.SHIP_TO_COUNTRY, "US", false),
                new PluginProperty(InvoicePluginPropertyNames.SHIP_TO_POSTAL_CODE, "94105", false),
                new PluginProperty(InvoicePluginPropertyNames.TAX_REGISTRATION_NUMBER, "12-3456789", false));

        final AccountTaxMetadata metadata = resolver.resolve(
                invoice,
                Mockito.mock(Account.class),
                properties,
                tenantContext(tenantId),
                aviateConfig(),
                TENANT_API_KEY,
                TENANT_API_SECRET);

        Assert.assertNotNull(metadata.getShipToAddress());
        Assert.assertEquals(metadata.getShipToAddress().getCountry(), "US");
        Assert.assertEquals(metadata.getTaxRegistrationNumber(), "12-3456789");
        Assert.assertEquals(metadata.getSource(), AccountTaxMetadata.Source.CUSTOM_FIELDS);
        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo(
                "/plugins/aviate-plugin/v1/ba/forKbAccountId/" + accountId)));
    }

    @Test(groups = "fast")
    public void testExplicitTaxExemptFalseOverridesAviateExemption() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final UUID tenantId = UUID.randomUUID();

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of());

        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getCountry()).thenReturn("US");

        final String body = "{\"taxRegistrations\":[{\"exempt\":true,\"trn\":\"98-7654321\","
                + "\"address\":{\"country\":\"US\",\"postalCode\":\"02101\"}}]}";
        wireMockServer.stubFor(get(urlPathEqualTo("/plugins/aviate-plugin/v1/ba/forKbAccountId/" + accountId))
                .willReturn(aResponse().withStatus(200).withBody(body)));

        final OSGIKillbillAPI killbillAPI = mockCustomFields(accountId, tenantId, List.of(), List.of());
        final TaxMetadataResolver resolver = new TaxMetadataResolver(killbillAPI);

        final List<PluginProperty> properties = List.of(
                new PluginProperty(TaxMetadataResolver.TAX_EXEMPT, "false", false));

        final AccountTaxMetadata metadata = resolver.resolve(
                invoice, account, properties, tenantContext(tenantId), aviateConfig(),
                TENANT_API_KEY, TENANT_API_SECRET);

        Assert.assertFalse(metadata.isTaxExempt());
        Assert.assertEquals(metadata.getSource(), AccountTaxMetadata.Source.AVIATE_BILLING_ACCOUNT);
    }

    @Test(groups = "fast")
    public void testAviateHttpFailureFallsBackToCustomFields() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final UUID tenantId = UUID.randomUUID();

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of());

        wireMockServer.stubFor(get(urlPathEqualTo("/plugins/aviate-plugin/v1/ba/forKbAccountId/" + accountId))
                .willReturn(aResponse().withStatus(503).withBody("unavailable")));

        final CustomField exemptField = customField(accountId, ObjectType.ACCOUNT, TaxMetadataResolver.TAX_EXEMPT, "true");
        final OSGIKillbillAPI killbillAPI = mockCustomFields(
                accountId, tenantId, List.of(exemptField), List.of());
        final TaxMetadataResolver resolver = new TaxMetadataResolver(killbillAPI);

        final AccountTaxMetadata metadata = resolver.resolve(
                invoice,
                Mockito.mock(Account.class),
                List.of(),
                tenantContext(tenantId),
                aviateConfig(),
                TENANT_API_KEY,
                TENANT_API_SECRET);

        Assert.assertEquals(metadata.getSource(), AccountTaxMetadata.Source.CUSTOM_FIELDS);
        Assert.assertTrue(metadata.isTaxExempt());
        Assert.assertNull(metadata.getShipToAddress());
    }

    @Test(groups = "fast")
    public void testAviateBillingAccountOverridesAddressAndExemption() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final UUID tenantId = UUID.randomUUID();

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of());

        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getCountry()).thenReturn("US");

        final String body = "{"
                + "\"companyName\":\"CloudSprout Inc.\","
                + "\"email\":\"billing@cloudsprout.example\","
                + "\"address\":{\"addressLine1\":\"100 Main Street\",\"city\":\"San Francisco\","
                + "\"state\":\"CA\",\"country\":\"US\",\"postalCode\":\"94105\"},"
                + "\"taxRegistrations\":[{\"name\":\"US Tax Registration\",\"exempt\":false,"
                + "\"trn\":\"12-3456789\",\"address\":{\"addressLine1\":\"100 Main Street\","
                + "\"city\":\"San Francisco\",\"state\":\"CA\",\"country\":\"US\",\"postalCode\":\"94105\"}}]"
                + "}";
        wireMockServer.stubFor(get(urlPathEqualTo("/plugins/aviate-plugin/v1/ba/forKbAccountId/" + accountId))
                .willReturn(aResponse().withStatus(200).withBody(body)));

        final OSGIKillbillAPI killbillAPI = mockCustomFields(accountId, tenantId, List.of(), List.of());
        final TaxMetadataResolver resolver = new TaxMetadataResolver(killbillAPI);
        final KintsugiTenantConfig config = aviateConfig();

        final AccountTaxMetadata metadata = resolver.resolve(
                invoice, account, List.of(), tenantContext(tenantId), config,
                TENANT_API_KEY, TENANT_API_SECRET);

        Assert.assertEquals(metadata.getSource(), AccountTaxMetadata.Source.AVIATE_BILLING_ACCOUNT);
        Assert.assertEquals(metadata.getCompanyName(), "CloudSprout Inc.");
        Assert.assertEquals(metadata.getTaxRegistrationNumber(), "12-3456789");
        Assert.assertNotNull(metadata.getShipToAddress());
        Assert.assertEquals(metadata.getShipToAddress().getCountry(), "US");

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/plugins/aviate-plugin/v1/ba/forKbAccountId/" + accountId))
                .withHeader("Authorization", equalTo("Bearer " + AVIATE_TOKEN))
                .withHeader("X-Killbill-ApiKey", equalTo(TENANT_API_KEY))
                .withHeader("X-Killbill-ApiSecret", equalTo(TENANT_API_SECRET)));
    }

    @Test(groups = "fast")
    public void testAviate404FallsBackToCustomFields() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final UUID tenantId = UUID.randomUUID();

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of());

        wireMockServer.stubFor(get(urlPathEqualTo("/plugins/aviate-plugin/v1/ba/forKbAccountId/" + accountId))
                .willReturn(aResponse().withStatus(404)));

        final CustomField exemptField = customField(accountId, ObjectType.ACCOUNT, TaxMetadataResolver.TAX_EXEMPT, "true");
        final OSGIKillbillAPI killbillAPI = mockCustomFields(
                accountId, tenantId, List.of(exemptField), List.of());
        final TaxMetadataResolver resolver = new TaxMetadataResolver(killbillAPI);

        final AccountTaxMetadata metadata = resolver.resolve(
                invoice,
                Mockito.mock(Account.class),
                List.of(),
                tenantContext(tenantId),
                aviateConfig(),
                TENANT_API_KEY,
                TENANT_API_SECRET);

        Assert.assertEquals(metadata.getSource(), AccountTaxMetadata.Source.CUSTOM_FIELDS);
        Assert.assertTrue(metadata.isTaxExempt());
        Assert.assertNull(metadata.getShipToAddress());
    }

    @Test(groups = "fast")
    public void testAviateExemptRegistrationSetsTaxExempt() throws Exception {
        final AviateBillingAccount billingAccount = AviateBillingAccount.fromJson(new ObjectMapper().readTree(
                "{\"taxRegistrations\":[{\"exempt\":true,\"trn\":\"98-7654321\","
                        + "\"address\":{\"country\":\"US\",\"postalCode\":\"02101\"}}]}"));
        Assert.assertNotNull(billingAccount);
        Assert.assertTrue(billingAccount.isTaxExempt());
        Assert.assertEquals(billingAccount.resolveTaxRegistrationNumber("US"), "98-7654321");
    }

    private KintsugiTenantConfig baseConfig() {
        final KintsugiTenantConfig config = new KintsugiTenantConfig();
        config.setKintsugiUrl("https://api.example.com");
        config.setHmacSecret("secret");
        return config;
    }

    private KintsugiTenantConfig aviateConfig() {
        final KintsugiTenantConfig config = baseConfig();
        config.setKillbillUrl("http://localhost:" + wireMockServer.port());
        config.setAviateIdToken(AVIATE_TOKEN);
        return config;
    }

    private static OSGIKillbillAPI mockCustomFields(
            final UUID accountId,
            final UUID tenantId,
            final List<CustomField> accountFields,
            final List<CustomField> itemFields) throws Exception {
        final OSGIKillbillAPI killbillAPI = Mockito.mock(OSGIKillbillAPI.class);
        final CustomFieldUserApi customFieldUserApi = Mockito.mock(CustomFieldUserApi.class);
        Mockito.when(killbillAPI.getCustomFieldUserApi()).thenReturn(customFieldUserApi);
        Mockito.when(customFieldUserApi.getCustomFieldsForObject(
                Mockito.eq(accountId), Mockito.eq(ObjectType.ACCOUNT), Mockito.any(TenantContext.class)))
                .thenReturn(accountFields);
        Mockito.when(customFieldUserApi.getCustomFieldsForAccountType(
                Mockito.eq(accountId), Mockito.eq(ObjectType.INVOICE_ITEM), Mockito.any(TenantContext.class)))
                .thenReturn(itemFields);
        return killbillAPI;
    }

    private static CustomField customField(
            final UUID objectId,
            final ObjectType objectType,
            final String fieldName,
            final String value) {
        final CustomField field = Mockito.mock(CustomField.class);
        Mockito.when(field.getObjectId()).thenReturn(objectId);
        Mockito.when(field.getObjectType()).thenReturn(objectType);
        Mockito.when(field.getFieldName()).thenReturn(fieldName);
        Mockito.when(field.getFieldValue()).thenReturn(value);
        return field;
    }

    private static TenantContext tenantContext(final UUID tenantId) {
        return new PluginCallContext(
                "killbill-kintsugi",
                new DefaultClock().getUTCNow(),
                UUID.randomUUID(),
                tenantId);
    }
}
