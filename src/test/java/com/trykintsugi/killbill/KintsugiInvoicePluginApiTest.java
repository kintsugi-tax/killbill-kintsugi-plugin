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

import com.sun.net.httpserver.HttpServer;
import com.trykintsugi.killbill.internal.KintsugiTaxClient;
import org.joda.time.Period;
import org.junit.jupiter.api.Test;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApiRetryException;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KintsugiInvoicePluginApiTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID INVOICE_ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void documentErrorUsesInvoiceRetrySchedule() throws Exception {
        final HttpServer server = startServer();
        final OSGIKillbillAPI killbillApi = createKillbillApi();
        try {
            final KintsugiTenantConfig config = new KintsugiTenantConfig();
            config.setKintsugiUrl("http://127.0.0.1:" + server.getAddress().getPort());
            config.setHmacSecret("secret");

            final KintsugiConfigurationHandler configurationHandler =
                    new KintsugiConfigurationHandler("killbill-kintsugi", killbillApi);
            configurationHandler.setDefaultConfigurable(config);
            final KintsugiInvoicePluginApi plugin = new KintsugiInvoicePluginApi(
                    killbillApi,
                    null,
                    null,
                    configurationHandler);

            final InvoicePluginApiRetryException exception = assertThrows(
                    InvoicePluginApiRetryException.class,
                    () -> plugin.getAdditionalInvoiceItems(
                            invoice(),
                            true,
                            List.of(),
                            invoiceContext()));

            assertInstanceOf(KintsugiTaxClient.TaxEstimationException.class, exception.getCause());
            assertEquals(
                    List.of(Period.minutes(1), Period.minutes(5), Period.minutes(15)),
                    exception.getRetrySchedule());
        } finally {
            killbillApi.close();
            server.stop(0);
        }
    }

    private static HttpServer startServer() throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/killbill/tax/estimate",
                exchange -> {
                    final byte[] response = (
                            "{\"documents\":[{\"document_id\":\"" + INVOICE_ID + "\","
                                    + "\"error\":{\"code\":\"PRODUCTS_NOT_FOUND\","
                                    + "\"message\":\"Product p2-monthly not found\","
                                    + "\"details\":\"ProductNotFound\"}}]}")
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, response.length);
                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(response);
                    }
                });
        server.start();
        return server;
    }

    private static OSGIKillbillAPI createKillbillApi() {
        final Account account = proxy(
                Account.class,
                Map.of(
                        "getCountry", "US",
                        "getPostalCode", "94102",
                        "getStateOrProvince", "CA",
                        "getCity", "San Francisco",
                        "getAddress1", "1 Main Street",
                        "getExternalKey", "account-1"));
        final AccountUserApi accountUserApi = proxy(
                AccountUserApi.class,
                Map.of("getAccountById", account));
        final Tenant tenant = proxy(Tenant.class, Map.of("getApiKey", "tenant-api-key"));
        final TenantUserApi tenantUserApi = proxy(
                TenantUserApi.class,
                Map.of("getTenantById", tenant));

        return new OSGIKillbillAPI(proxy(BundleContext.class, Map.of())) {
            @Override
            public AccountUserApi getAccountUserApi() {
                return accountUserApi;
            }

            @Override
            public TenantUserApi getTenantUserApi() {
                return tenantUserApi;
            }
        };
    }

    private static Invoice invoice() {
        final InvoiceItem invoiceItem = proxy(
                InvoiceItem.class,
                Map.of(
                        "getId", INVOICE_ITEM_ID,
                        "getAmount", new BigDecimal("100.00"),
                        "getQuantity", BigDecimal.ONE,
                        "getDescription", "Monthly subscription",
                        "getInvoiceItemType", InvoiceItemType.RECURRING,
                        "getPlanName", "p2-monthly",
                        "getPrettyProductName", "Product 2"));
        return proxy(
                Invoice.class,
                Map.of(
                        "getInvoiceItems", List.of(invoiceItem),
                        "getAccountId", ACCOUNT_ID,
                        "getId", INVOICE_ID,
                        "getCurrency", Currency.USD,
                        "getInvoiceDate", org.joda.time.LocalDate.parse("2026-08-18")));
    }

    private static InvoiceContext invoiceContext() {
        return proxy(InvoiceContext.class, Map.of());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(final Class<T> type, final Map<String, Object> values) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) {
                        return type.getSimpleName();
                    }
                    return values.containsKey(method.getName())
                            ? values.get(method.getName())
                            : defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(final Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == double.class) {
            return 0.0d;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
