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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TestInvoiceRequestMapper {

    @Test(groups = "fast")
    public void testFormatAmountUsesTwoDecimalPlaces() {
        Assert.assertEquals(InvoiceRequestMapper.formatAmount(new BigDecimal("100")), "100.00");
        Assert.assertEquals(InvoiceRequestMapper.formatAmount(new BigDecimal("99.994")), "99.99");
        Assert.assertEquals(InvoiceRequestMapper.formatAmount(null), "0.00");
    }

    @Test(groups = "fast")
    public void testFormatQuantityDefaultsToOne() {
        Assert.assertEquals(InvoiceRequestMapper.formatQuantity(null), "1");
        Assert.assertEquals(InvoiceRequestMapper.formatQuantity(new BigDecimal("2.0")), "2");
    }

    @Test(groups = "fast")
    public void testExternalChargeUsesDefaultProductLabels() {
        Assert.assertEquals(InvoiceRequestMapper.EXTERNAL_CHARGE_CATEGORY, "Physical");
        Assert.assertEquals(InvoiceRequestMapper.EXTERNAL_CHARGE_SUBCATEGORY, "General Physical");
    }

    @Test(groups = "fast")
    public void testToEstimateRequestMapsInvoiceFields() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID itemId = UUID.randomUUID();

        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(account.getExternalKey()).thenReturn("acct-ext");
        Mockito.when(account.getCountry()).thenReturn("US");
        Mockito.when(account.getPostalCode()).thenReturn("94105");
        Mockito.when(account.getStateOrProvince()).thenReturn("CA");
        Mockito.when(account.getCity()).thenReturn("San Francisco");
        Mockito.when(account.getAddress1()).thenReturn("1 Market St");
        Mockito.when(account.getEmail()).thenReturn("billing@example.com");
        Mockito.when(account.getName()).thenReturn("Example Inc");

        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        Mockito.when(item.getId()).thenReturn(itemId);
        Mockito.when(item.getAmount()).thenReturn(new BigDecimal("100.00"));
        Mockito.when(item.getQuantity()).thenReturn(BigDecimal.ONE);
        Mockito.when(item.getDescription()).thenReturn("Widget");
        Mockito.when(item.getInvoiceItemType()).thenReturn(InvoiceItemType.EXTERNAL_CHARGE);
        Mockito.when(item.getPlanName()).thenReturn(null);
        Mockito.when(item.getPrettyProductName()).thenReturn(null);

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getId()).thenReturn(invoiceId);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(invoice.getInvoiceDate()).thenReturn(new LocalDate(2026, 1, 15));
        Mockito.when(invoice.getInvoiceNumber()).thenReturn(42);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of(item));

        final ObjectNode request = InvoiceRequestMapper.toEstimateRequest(
                invoice, account, true, "tenant-1");

        Assert.assertEquals(request.path("currency_code").asText(), "USD");
        Assert.assertEquals(request.path("plugin_name").asText(), "killbill-kintsugi");
        Assert.assertEquals(request.path("tenant_id").asText(), "tenant-1");

        final ObjectNode document = (ObjectNode) request.path("documents").get(0);
        Assert.assertTrue(document.path("dry_run").asBoolean());
        Assert.assertEquals(document.path("transaction_date").asText(), "2026-01-15");
        Assert.assertEquals(document.path("invoice_number").asInt(), 42);

        final ObjectNode line = (ObjectNode) document.path("line_items").get(0);
        Assert.assertEquals(line.path("external_id").asText(), itemId.toString());
        Assert.assertEquals(line.path("product_category").asText(), "Physical");
        Assert.assertEquals(line.path("product_subcategory").asText(), "General Physical");
    }

    @Test(groups = "fast")
    public void testSkipsTaxAndAdjustmentItems() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID taxableItemId = UUID.randomUUID();

        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(account.getExternalKey()).thenReturn("acct-ext");

        final InvoiceItem taxableItem = Mockito.mock(InvoiceItem.class);
        Mockito.when(taxableItem.getId()).thenReturn(taxableItemId);
        Mockito.when(taxableItem.getAmount()).thenReturn(new BigDecimal("100.00"));
        Mockito.when(taxableItem.getQuantity()).thenReturn(BigDecimal.ONE);
        Mockito.when(taxableItem.getInvoiceItemType()).thenReturn(InvoiceItemType.EXTERNAL_CHARGE);

        final InvoiceItem taxItem = Mockito.mock(InvoiceItem.class);
        Mockito.when(taxItem.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(taxItem.getAmount()).thenReturn(new BigDecimal("8.25"));
        Mockito.when(taxItem.getInvoiceItemType()).thenReturn(InvoiceItemType.TAX);

        final InvoiceItem adjustmentItem = Mockito.mock(InvoiceItem.class);
        Mockito.when(adjustmentItem.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(adjustmentItem.getAmount()).thenReturn(new BigDecimal("-10.00"));
        Mockito.when(adjustmentItem.getInvoiceItemType()).thenReturn(InvoiceItemType.ITEM_ADJ);

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getId()).thenReturn(invoiceId);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(invoice.getInvoiceDate()).thenReturn(new LocalDate(2026, 1, 15));
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of(taxableItem, taxItem, adjustmentItem));

        final ObjectNode request = InvoiceRequestMapper.toEstimateRequest(
                invoice, account, true, null);
        final ObjectNode document = (ObjectNode) request.path("documents").get(0);

        Assert.assertEquals(document.path("line_items").size(), 1);
        Assert.assertEquals(
                document.path("line_items").get(0).path("external_id").asText(),
                taxableItemId.toString());

        final Map<UUID, String> externalIds = InvoiceRequestMapper.externalIdsForInvoice(invoice);
        Assert.assertEquals(externalIds.size(), 1);
        Assert.assertEquals(externalIds.get(taxableItemId), taxableItemId.toString());
    }

    @Test(groups = "fast")
    public void testPlanNameIsMappedToProductFields() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID itemId = UUID.randomUUID();

        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(account.getExternalKey()).thenReturn("acct-ext");

        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        Mockito.when(item.getId()).thenReturn(itemId);
        Mockito.when(item.getAmount()).thenReturn(new BigDecimal("10.00"));
        Mockito.when(item.getQuantity()).thenReturn(BigDecimal.ONE);
        Mockito.when(item.getInvoiceItemType()).thenReturn(InvoiceItemType.RECURRING);
        Mockito.when(item.getPlanName()).thenReturn("basic-monthly");
        Mockito.when(item.getPrettyProductName()).thenReturn("Basic");

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getId()).thenReturn(invoiceId);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(invoice.getInvoiceDate()).thenReturn(new LocalDate(2026, 1, 15));
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of(item));

        final ObjectNode request = InvoiceRequestMapper.toEstimateRequest(
                invoice, account, false, null);
        final ObjectNode line = (ObjectNode) request.path("documents").get(0).path("line_items").get(0);

        Assert.assertEquals(line.path("plan_name").asText(), "basic-monthly");
        Assert.assertEquals(line.path("external_product_id").asText(), "basic-monthly");
        Assert.assertEquals(line.path("product_name").asText(), "Basic");
        Assert.assertFalse(line.has("product_category"));
    }

    @Test(groups = "fast")
    public void testTaxMetadataEnrichesCustomerAndLines() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID itemId = UUID.randomUUID();

        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(account.getExternalKey()).thenReturn("acct-ext");

        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        Mockito.when(item.getId()).thenReturn(itemId);
        Mockito.when(item.getAmount()).thenReturn(new BigDecimal("10.00"));
        Mockito.when(item.getQuantity()).thenReturn(BigDecimal.ONE);
        Mockito.when(item.getInvoiceItemType()).thenReturn(InvoiceItemType.EXTERNAL_CHARGE);

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getId()).thenReturn(invoiceId);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(invoice.getInvoiceDate()).thenReturn(new LocalDate(2026, 1, 15));
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of(item));

        final AccountTaxMetadata metadata = AccountTaxMetadata.builder()
                .taxExempt(true)
                .customerUsageType("G")
                .taxCodeByInvoiceItemId(Map.of(itemId, "P0000000"))
                .build();

        final ObjectNode request = InvoiceRequestMapper.toEstimateRequest(
                invoice, account, true, null, metadata);
        final ObjectNode document = (ObjectNode) request.path("documents").get(0);
        final ObjectNode customer = (ObjectNode) document.path("customer");

        Assert.assertTrue(customer.path("exempt").asBoolean());
        Assert.assertEquals(customer.path("entity_use_code").asText(), "G");
        Assert.assertEquals(
                document.path("line_items").get(0).path("tax_code").asText(),
                "P0000000");
    }

    @Test(groups = "fast")
    public void testAviateShipToAddressOverridesAccountAddress() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID itemId = UUID.randomUUID();

        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(account.getExternalKey()).thenReturn("acct-ext");
        Mockito.when(account.getCountry()).thenReturn("US");
        Mockito.when(account.getPostalCode()).thenReturn("78701");

        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        Mockito.when(item.getId()).thenReturn(itemId);
        Mockito.when(item.getAmount()).thenReturn(new BigDecimal("10.00"));
        Mockito.when(item.getQuantity()).thenReturn(BigDecimal.ONE);
        Mockito.when(item.getInvoiceItemType()).thenReturn(InvoiceItemType.EXTERNAL_CHARGE);

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getId()).thenReturn(invoiceId);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
        Mockito.when(invoice.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(invoice.getInvoiceDate()).thenReturn(new LocalDate(2026, 1, 15));
        Mockito.when(invoice.getInvoiceItems()).thenReturn(List.of(item));

        final AccountTaxMetadata metadata = AccountTaxMetadata.builder()
                .source(AccountTaxMetadata.Source.AVIATE_BILLING_ACCOUNT)
                .companyName("CloudSprout Inc.")
                .taxRegistrationNumber("12-3456789")
                .shipToAddress(new TaxAddress(
                        "100 Main Street", null, "San Francisco", "CA", "US", "94105"))
                .build();

        final ObjectNode request = InvoiceRequestMapper.toEstimateRequest(
                invoice, account, true, null, metadata);
        final ObjectNode document = (ObjectNode) request.path("documents").get(0);

        Assert.assertEquals(document.path("ship_to").path("postal_code").asText(), "94105");
        Assert.assertEquals(document.path("customer").path("name").asText(), "CloudSprout Inc.");
        Assert.assertEquals(
                document.path("customer").path("tax_registration_number").asText(),
                "12-3456789");
    }
}
